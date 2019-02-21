/**
 * Created by tschmidt on 2/23/17.
 */
import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import {FormComponent} from './form.component';
import {FormResolve} from './form.resolover';
import {TabBasicsComponent} from './tab-basics/tab-basics.component';
import {TabLogoutComponent} from './tab-logout/tab-logout.component';
import {TabAccessstrategyComponent} from './tab-accessstrategy/tab-accessstrategy.component';
import {TabMulitauthComponent} from './tab-mulitauth/tab-mulitauth.component';
import {TabProxyComponent} from './tab-proxy/tab-proxy.component';
import {TabUsernameattrComponent} from './tab-usernameattr/tab-usernameattr.component';
import {TabAttrreleaseComponent} from './tab-attrrelease/tab-attrrelease.component';
import {TabPropertiesComponent} from './tab-properties/tab-properties.component';
import {TabAdvancedComponent} from './tab-advanced/tab-advanced.component';
import {TabSamlComponent} from './tab-saml/tab-saml.component';
import {TabOauthComponent} from './tab-oauth/tab-oauth.component';
import {TabWsfedComponent} from './tab-wsfed/tab-wsfed.component';
import {TabContactsComponent} from './tab-contacts/tab-contacts.component';
import {TabExpirationComponent} from './tab-expiration/tab-expiration.component';
import {TabOIDCComponent} from './tab-oidc/tab-oidc.component';
import {SamlFormResolve} from '../../../../saml/src/app/form/form.resolve';
import {SamlResolve} from '@app/form/saml-resolve';
import {OAuthResolve} from '@app/form/oauth-resolve';
import {OidcResolve} from '@app/form/oidc-resolve';

const childRoutes: Routes = [
  {
    path: 'basics',
    component: TabBasicsComponent,
    outlet: 'form'
  },
  {
    path: 'saml',
    component: TabSamlComponent,
    outlet: 'form'
  },
  {
    path: 'oauth',
    component: TabOauthComponent,
    outlet: 'form'
  },
  {
    path: 'oidc',
    component: TabOIDCComponent,
    outlet: 'form'
  },
  {
    path: 'wsfed',
    component: TabWsfedComponent,
    outlet: 'form'
  },
  {
    path: 'contacts',
    component: TabContactsComponent,
    outlet: 'form'
  },
  {
    path: 'logout',
    component: TabLogoutComponent,
    outlet: 'form'
  },
  {
    path: 'accessstrategy',
    component: TabAccessstrategyComponent,
    outlet: 'form'
  },
  {
    path: 'expiration',
    component: TabExpirationComponent,
    outlet: 'form'
  },
  {
    path: 'multiauth',
    component: TabMulitauthComponent,
    outlet: 'form'
  },
  {
    path: 'proxy',
    component: TabProxyComponent,
    outlet: 'form'
  },
  {
    path: 'userattr',
    component: TabUsernameattrComponent,
    outlet: 'form'
  },
  {
    path: 'attrRelease',
    component: TabAttrreleaseComponent,
    outlet: 'form'
  },
  {
    path: 'properties',
    component: TabPropertiesComponent,
    outlet: 'form'
  },
  {
    path: 'advanced',
    component: TabAdvancedComponent,
    outlet: 'form'
  }
];

@NgModule({
  imports: [
    RouterModule.forChild([
      {
        path: 'duplicate/:id',
        component: FormComponent,
        resolve: {
          resp: FormResolve
        },
        children: childRoutes,
        data: {
          duplicate: true,
        }
      },
      {
        path: 'view/:id',
        component: FormComponent,
        resolve: {
          resp: FormResolve
        },
        children: childRoutes,
        data: {
          view: true
        }
      },
      {
        path: 'edit/:id',
        component: FormComponent,
        resolve: {
          resp: FormResolve
        },
        children: childRoutes
      },
      {
        path: 'saml',
        component: FormComponent,
        resolve : {
          resp: SamlResolve
        },
        children: childRoutes,
        data: {
          created: true
        }
      },
      {
        path: 'oauth',
        component: FormComponent,
        resolve: {
          resp: OAuthResolve
        },
        children: childRoutes,
        data: {
          created: true
        }
      },
      {
        path: 'oidc',
        component: FormComponent,
        resolve: {
          resp: OidcResolve
        },
        children: childRoutes,
        data: {
          created: true
        }
      },
      {
        path: 'importService',
        component: FormComponent,
        children: childRoutes,
        resolve: {
          resp: FormResolve
        },
        data: {
          import: true
        }

      }
    ])
  ],
  exports: [ RouterModule ]
})

export class FormRoutingModule {}

