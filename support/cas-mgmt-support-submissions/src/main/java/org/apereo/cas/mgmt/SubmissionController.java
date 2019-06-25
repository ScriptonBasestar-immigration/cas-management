package org.apereo.cas.mgmt;

import org.apereo.cas.configuration.CasManagementConfigurationProperties;
import org.apereo.cas.mgmt.authentication.CasUserProfile;
import org.apereo.cas.mgmt.authentication.CasUserProfileFactory;
import org.apereo.cas.mgmt.controller.AbstractVersionControlController;
import org.apereo.cas.mgmt.controller.EmailManager;
import org.apereo.cas.mgmt.domain.PendingItem;
import org.apereo.cas.mgmt.domain.RegisteredServiceItem;
import org.apereo.cas.mgmt.domain.RejectData;
import org.apereo.cas.mgmt.factory.RepositoryFactory;
import org.apereo.cas.mgmt.util.CasManagementUtils;
import org.apereo.cas.services.OidcRegisteredService;
import org.apereo.cas.services.RegisteredService;
import org.apereo.cas.services.util.DefaultRegisteredServiceJsonSerializer;
import org.apereo.cas.support.oauth.services.OAuthRegisteredService;
import org.apereo.cas.util.DigestUtils;

import com.google.common.base.Splitter;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.diff.RawText;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.UserDefinedFileAttributeView;
import java.text.MessageFormat;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

/**
 * Controller class to handle accepting and rejecting submitted Services.
 *
 * @author Travis Schmidt
 * @since 5.3.0
 */
@RestController("casManagementSubmissisonsController")
@RequestMapping(path = "api/submissions", produces = MediaType.APPLICATION_JSON_VALUE)
@Slf4j
public class SubmissionController extends AbstractVersionControlController {

    private static final int MAX_EMAIL_LENGTH = 200;

    private final RepositoryFactory repositoryFactory;
    private final MgmtManagerFactory managerFactory;
    private final CasManagementConfigurationProperties managementProperties;
    private final EmailManager communicationsManager;

    public SubmissionController(final RepositoryFactory repositoryFactory,
                                final MgmtManagerFactory managerFactory,
                                final CasManagementConfigurationProperties managementProperties,
                                final CasUserProfileFactory casUserProfileFactory,
                                final EmailManager communicationsManager) {
        super(casUserProfileFactory);
        this.repositoryFactory = repositoryFactory;
        this.managerFactory = managerFactory;
        this.managementProperties = managementProperties;
        this.communicationsManager = communicationsManager;
    }

    /**
     * Mapped method to pull list of Submitted services from the queue.
     *
     * @param response - the response
     * @param request - the request
     * @return - List of RegisteredServiceItem
     * @throws Exception - failed
     */
    @GetMapping
    public List<RegisteredServiceItem> getSubmissions(final HttpServletResponse response,
                                                      final HttpServletRequest request) throws Exception {
        isAdministrator(request, response);
        LOGGER.debug(managementProperties.getSubmissions().getSubmitDir());
        try (Stream<Path> stream = Files.list(Paths.get(managementProperties.getSubmissions().getSubmitDir()))) {
            return stream.map(this::createServiceItem).collect(toList());
        } catch (final Exception e) {
            LOGGER.error(e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Returns list of pending submissions for logged in user.
     *
     * @param response - the response
     * @param request - the request
     * @param type - the type
     * @return - List of PendingItem
     * @throws Exception - failed
     */
    @GetMapping("/pending/{type}")
    public List<PendingItem> getPendingSubmissions(final HttpServletResponse response,
                                                   final HttpServletRequest request,
                                                   final @PathVariable String type) throws Exception {
        val casUserProfile = casUserProfileFactory.from(request, response);
        try (Stream<Path> stream = Files.list(Paths.get(managementProperties.getSubmissions().getSubmitDir()))) {
            val list = stream.filter(p -> isSubmitter(p, casUserProfile))
                    .filter(p -> isType(p, type))
                    .map(this::createPendingItem).collect(toList());

            val git = repositoryFactory.masterRepository();
            val bulks = git.branches()
                    .map(git::mapBranches)
                    .filter(b -> !b.getName().endsWith("master") && b.getCommitter().equalsIgnoreCase(casUserProfile.getId()))
                    .filter(r -> !r.isAccepted() && !r.isRejected())
                    .map(p -> createPendingItem(p, git))
                    .collect(toList());
            list.addAll(bulks);
            return list;

        } catch (final Exception e) {
            LOGGER.error(e.getMessage(), e);
            throw e;
        }
    }

    private boolean isSubmitter(final Path p, final CasUserProfile casUserProfile) {
        return getSubmitter(p)[0].equals(casUserProfile.getEmail());
    }

    private boolean isType(final Path p, final String type) {
        val service = CasManagementUtils.fromJson(p.toFile());
        if (service instanceof OAuthRegisteredService) {
            return "oauth".equals(type);
        }
        if (service.getClass().getName().contains("Saml")) {
            return "saml".equals(type);
        }
        return "cas".equals(type);
    }

    private RegisteredServiceItem createServiceItem(final Path p) {
        val serializer = new DefaultRegisteredServiceJsonSerializer();
        val service = serializer.from(p.toFile());
        val serviceItem = new RegisteredServiceItem();
        serviceItem.setAssignedId(p.getFileName().toString());
        serviceItem.setEvalOrder(service.getEvaluationOrder());
        serviceItem.setName(service.getName());
        serviceItem.setServiceId(service.getServiceId());
        serviceItem.setDescription(DigestUtils.abbreviate(service.getDescription()));
        serviceItem.setSubmitter(getSubmitter(p)[1]);
        serviceItem.setSubmitted(getSubmitted(p));
        serviceItem.setStatus(status(p.getFileName().toString()));
        serviceItem.setStaged(service.getEnvironments().contains("staged"));
        return serviceItem;
    }

    private PendingItem createPendingItem(final Path p) {
        LOGGER.debug("Path = " + p.toString());
        val service = CasManagementUtils.fromJson(p.toFile());
        val serviceItem = new PendingItem();
        serviceItem.setId(p.getFileName().toString());
        serviceItem.setName(service.getName());
        serviceItem.setServiceId(service.getServiceId());
        serviceItem.setSubmitted(getSubmitted(p));
        serviceItem.setStatus(status(p.getFileName().toString()));
        serviceItem.setType(getType(service));
        return serviceItem;
    }

    private PendingItem createPendingItem(final GitUtil.BranchMap p, final GitUtil git) {
        try {
            val serviceItem = new PendingItem();
            serviceItem.setId(p.getId());
            serviceItem.setName(p.getFullMessage());
            serviceItem.setServiceId(git.getDiffsToRevert(p.getName()).size() + " services");
            serviceItem.setSubmitted(CasManagementUtils.formatDateTime(p.getCommitTime()));
            serviceItem.setStatus("EDIT");
            return serviceItem;
        } catch (final Exception e) {
            LOGGER.error(e.getMessage(), e);
            return null;
        }
    }

    private String status(final String path) {
        if (path.startsWith("edit")) {
            return "EDIT";
        } else if (path.startsWith("remove")) {
            return "REMOVE";
        } else {
            return "SUBMITTED";
        }
    }

    private String getType(RegisteredService service) {
        if (service instanceof OidcRegisteredService) {
            return "oidc";
        }
        if (service instanceof OAuthRegisteredService) {
            return "oauth";
        }
        return "";
    }

    /**
     * Mapped method to return a submitted service in YAML format.
     *
     * @param response - the response
     * @param request - the request
     * @param id - file id of the submitted service
     * @return - YAML version of the service
     * @throws Exception - failed
     */
    @PostMapping("/yaml")
    public String getYamlSubmission(final HttpServletResponse response,
                                    final HttpServletRequest request,
                                    final @RequestBody String id) throws Exception {
        isAdministrator(request, response);
        val svc = CasManagementUtils.fromJson(new File(managementProperties.getSubmissions().getSubmitDir() +"/" + id));
        return CasManagementUtils.toYaml(svc);
    }

    /**
     * Mapped method to return a JSON representation of the submitted service.
     *
     * @param request - the request
     * @param response - the response
     * @param id - the file name of the service
     * @return - JSON version of the service
     * @throws Exception - failed
     */
    @PostMapping("/json")
    public String getJsonSubmission(final HttpServletRequest request,
                                    final HttpServletResponse response,
                                    final @RequestBody String id) throws Exception {
        isAdministrator(request, response);
        val svc = CasManagementUtils.fromJson(new File(managementProperties.getSubmissions().getSubmitDir() + "/" +id));
        return CasManagementUtils.toJson(svc);
    }

    /**
     * Mapped method to delete a submission from the queue.
     *
     * @param response - the response
     * @param request - the request
     * @param data - RejectData
     * @throws Exception - failed
     */
    @PostMapping(path = "/reject", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK)
    public void rejectSubmission(final HttpServletResponse response,
                                 final HttpServletRequest request,
                                 final @RequestBody RejectData data) throws Exception {
        isAdministrator(request, response);
        val path = Paths.get(managementProperties.getSubmissions().getSubmitDir() + "/" + data.getId());
        val service = CasManagementUtils.fromJson(path.toFile());
        val contact = getSubmitter(path)[0];
        Files.delete(path);
        sendRejectMessage(service.getName(), data.getNote(),
                 contact, data.getId().contains("edit"));
    }

    private void sendRejectMessage(final String submitName, final String note, final String email, final boolean isChange) {
        if (communicationsManager.isMailSenderDefined()) {
            val notifications = managementProperties.getSubmissions().getNotifications();
            val emailProps = isChange ? notifications.getRejectChange() : notifications.getReject();
            communicationsManager.email(
                    MessageFormat.format(emailProps.getText(), submitName, note),
                    emailProps.getFrom(),
                    MessageFormat.format(emailProps.getSubject(), submitName),
                    email,
                    emailProps.getCc(),
                    emailProps.getBcc()
            );
        }
    }

    /**
     * Mapped method to delete a submission from the queue.
     *
     * @param response - the response
     * @param request - the request
     * @param id - file name of the service
     * @throws Exception - failed
     */
    @PostMapping("added")
    @ResponseStatus(HttpStatus.OK)
    public void addedSubmission(final HttpServletResponse response,
                                final HttpServletRequest request,
                                final @RequestBody String id) throws Exception {
        isAdministrator(request, response);
        val path = Paths.get(managementProperties.getSubmissions().getSubmitDir() + "/" + id);
        val service = CasManagementUtils.fromJson(path.toFile());
        val contact = getSubmitter(path)[0];
        Files.delete(path);
        sendAddedMessage(service.getName(), "", contact);
    }

    private void sendAddedMessage(final String submitName, final String note, final String email) {
        if (communicationsManager.isMailSenderDefined()) {
            val emailProps = managementProperties.getSubmissions().getNotifications().getAdded();
            communicationsManager.email(
                    MessageFormat.format(emailProps.getText(), submitName, note),
                    emailProps.getFrom(),
                    MessageFormat.format(emailProps.getSubject(), submitName),
                    email,
                    emailProps.getCc(),
                    emailProps.getBcc()
            );
        }
    }
    /**
     * Mapped method that will return a diff of the submission and the current version in the repo.
     *
     * @param response - the response
     * @param request - the request
     * @param id - the file name of the submission
     * @throws Exception - failed
     */
    @PostMapping("diff")
    @ResponseStatus(HttpStatus.OK)
    public void diffSubmission(final HttpServletResponse response,
                               final HttpServletRequest request,
                               final @RequestBody String id) throws Exception {
        isAdministrator(request, response);
        val git = repositoryFactory.masterRepository();
        val subPath = new RawText(FileUtils.readFileToByteArray(
                new File(managementProperties.getSubmissions().getSubmitDir() + "/" + id)));
        val splitSub = Splitter.on("-").splitToList(id);
        val gitPath = new RawText(FileUtils.readFileToByteArray(
                new File(managementProperties.getVersionControl().getServicesRepo() + "/service-" + splitSub.get(1))));
        response.getOutputStream().write(git.getFormatter(gitPath, subPath));
    }

    /**
     * Mapped method to accept submissions.
     *
     * @param response - the response
     * @param request - the request
     * @param id - the file name of the submission
     * @throws Exception - failed
     */
    @PostMapping("accept")
    @ResponseStatus(HttpStatus.OK)
    public void acceptSubmission(final HttpServletResponse response,
                                 final HttpServletRequest request,
                                 final @RequestBody String id) throws Exception {
        isAdministrator(request, response);
        val manager = managerFactory.from(request, response);
        val path = Paths.get(managementProperties.getSubmissions().getSubmitDir() + "/" + id);
        val service = CasManagementUtils.fromJson(path.toFile());
        manager.save(service);
        val contact = getSubmitter(path)[0];
        Files.delete(path);
        sendAcceptMessage(service.getName(), contact);
    }

    private void sendAcceptMessage(final String submitName, final String email) {
        if (communicationsManager.isMailSenderDefined()) {
            val emailProps = managementProperties.getSubmissions().getNotifications().getAccept();
            communicationsManager.email(
                    MessageFormat.format(emailProps.getText(), submitName),
                    emailProps.getFrom(),
                    MessageFormat.format(emailProps.getSubject(), submitName),
                    email,
                    emailProps.getCc(),
                    emailProps.getBcc()
            );
        }
    }

    /**
     * Mapped method to accept removal of service submissions.
     *
     * @param response - the response
     * @param request - the request
     * @param id - the file name of the submission
     * @throws Exception - failed
     */
    @DeleteMapping
    @ResponseStatus(HttpStatus.OK)
    public void deleteSubmission(final HttpServletResponse response,
                                 final HttpServletRequest request,
                                 final @RequestParam String id) throws Exception {
        isAdministrator(request, response);
        val manager = managerFactory.from(request, response);
        val path = Paths.get(managementProperties.getSubmissions().getSubmitDir() + "/" + id);
        val service = CasManagementUtils.fromJson(path.toFile());
        val contact = getSubmitter(path)[0];
        manager.delete(service.getId());
        Files.delete(path);
        sendDeleteMessage(service.getName(), contact);
    }

    private void sendDeleteMessage(final String submitName, final String email) {
        if (communicationsManager.isMailSenderDefined()) {
            val emailProps = managementProperties.getSubmissions().getNotifications().getDelete();
            communicationsManager.email(
                    MessageFormat.format(emailProps.getText(), submitName),
                    emailProps.getFrom(),
                    MessageFormat.format(emailProps.getSubject(), submitName),
                    email,
                    emailProps.getCc(),
                    emailProps.getBcc()
            );
        }
    }

    /**
     * Returns submitted service file as {@link RegisteredService}.
     *
     * @param response - the response
     * @param request - the request
     * @param id - the assigned id
     * @return - RegisteredService
     * @throws Exception -failed
     */
    @PostMapping("import")
    public RegisteredService importSubmission(final HttpServletResponse response,
                                              final HttpServletRequest request,
                                              final @RequestBody String id) throws Exception {
        //isAdministrator(request, response);
        val service = CasManagementUtils.fromJson(
                new File(managementProperties.getSubmissions().getSubmitDir() + "/" + id));
        return service;
    }

    private String[] getSubmitter(final Path path) {
        try {
            val email = new byte[MAX_EMAIL_LENGTH];
            Files.getFileAttributeView(path, UserDefinedFileAttributeView.class)
                    .read("original_author", ByteBuffer.wrap(email));
            return new String(email, StandardCharsets.UTF_8).trim().split(":");
        } catch (final Exception e) {
            LOGGER.error(e.getMessage(), e);
            return new String[] {"", ""};
        }
    }

    private String getSubmitted(final Path path) {
        try {
            return LocalDateTime.ofInstant(Files.getLastModifiedTime(path).toInstant(), ZoneOffset.systemDefault()).toString();
        } catch (final Exception e) {
            LOGGER.error(e.getMessage(), e);
            return "";
        }
    }
}
