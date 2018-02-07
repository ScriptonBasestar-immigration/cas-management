export class GitStatus {
  hasChanges: boolean;
  added: String[];
  modified: String[];
  deleted: String[];
  unpublished: boolean;
  pendingSubmits: boolean;
}
