/*
 * Copyright 2014, Red Hat, Inc. and individual contributors as indicated by the
 * @author tags. See the copyright.txt file in the distribution for a full
 * listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This software is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this software; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA, or see the FSF
 * site: http://www.fsf.org.
 */
package org.zanata.action;

import static org.jboss.seam.international.StatusMessage.Severity.ERROR;
import static org.jboss.seam.international.StatusMessage.Severity.INFO;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

import javax.faces.context.ExternalContext;
import javax.persistence.EntityManager;
import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import org.hibernate.validator.constraints.Email;
import org.hibernate.validator.constraints.NotEmpty;
import org.jboss.seam.ScopeType;
import org.jboss.seam.annotations.Create;
import org.jboss.seam.annotations.In;
import org.jboss.seam.annotations.Name;
import org.jboss.seam.annotations.Scope;
import org.jboss.seam.annotations.Transactional;
import org.jboss.seam.core.Conversation;
import org.jboss.seam.faces.FacesMessages;
import org.jboss.seam.faces.Renderer;
import org.jboss.seam.security.RunAsOperation;
import org.jboss.seam.security.management.IdentityManager;
import org.jboss.seam.security.management.JpaIdentityStore;
import org.zanata.dao.AccountDAO;
import org.zanata.dao.CredentialsDAO;
import org.zanata.dao.PersonDAO;
import org.zanata.i18n.Messages;
import org.zanata.model.HAccount;
import org.zanata.model.HLocale;
import org.zanata.model.HPerson;
import org.zanata.model.security.HCredentials;
import org.zanata.model.security.HOpenIdCredentials;
import org.zanata.security.AuthenticationManager;
import org.zanata.security.openid.FedoraOpenIdProvider;
import org.zanata.security.openid.GoogleOpenIdProvider;
import org.zanata.security.openid.OpenIdAuthCallback;
import org.zanata.security.openid.OpenIdAuthenticationResult;
import org.zanata.security.openid.OpenIdProviderType;
import org.zanata.security.openid.YahooOpenIdProvider;
import org.zanata.service.LanguageTeamService;
import org.zanata.service.impl.EmailChangeService;

import com.google.common.collect.Lists;
import org.zanata.util.ComparatorUtil;
import org.zanata.util.ServiceLocator;

/**
 * This is an action class that should eventually replace the
 * {@link org.zanata.action.ProfileAction} class as the UI controller for user
 * settings.
 *
 * @author Carlos Munoz <a
 *         href="mailto:camunoz@redhat.com">camunoz@redhat.com</a>
 * @see {@link org.zanata.action.ProfileAction}
 */
@Name("userSettingsAction")
@Scope(ScopeType.PAGE)
@Slf4j
public class UserSettingsAction {

    @In(create = true)
    protected Renderer renderer;

    @In
    private EmailChangeService emailChangeService;

    @In(create = true)
    private ProfileAction profileAction;

    @In
    private PersonDAO personDAO;

    @In
    private AccountDAO accountDAO;

    @In
    private IdentityManager identityManager;

    @In
    private AuthenticationManager authenticationManager;

    @In
    private LanguageTeamService languageTeamServiceImpl;

    @In
    private Messages msgs;

    @In(value = JpaIdentityStore.AUTHENTICATED_USER)
    HAccount authenticatedAccount;

    @Getter
    @Setter
    @Email
    @NotEmpty
    private String emailAddress;

    @Getter
    @Setter
    @NotEmpty
    @Size(min = 6, max = 20)
    private String newPassword;

    @Getter
    @Setter
    @NotEmpty
    private String oldPassword;

    @Getter
    @Setter
    private String openId;

    @Getter
    @Setter
    @NotEmpty
    @Size(min = 2, max = 80)
    private String accountName;

    @Create
    public void onCreate() {
        HPerson person =
                personDAO.findById(authenticatedAccount.getPerson().getId());
        emailAddress = person.getEmail();
        accountName = person.getName();
    }

    public void updateEmail() {
        if(!isEmailAddressValid(emailAddress)) {
            FacesMessages.instance().addToControl("email",
                    "This email address is already taken");
            return;
        }

        HPerson person =
                personDAO.findById(authenticatedAccount.getPerson().getId(),
                        true);
        if (!authenticatedAccount.getPerson().getEmail().equals(emailAddress)) {
            String activationKey =
                    emailChangeService.generateActivationKey(person,
                            emailAddress);
            // TODO create a separate field for newEmail, perhaps in this class
            // TODO should template and messages.properties use userSettingsAction, not profileAction?
            profileAction.setEmail(emailAddress);
            profileAction.setActivationKey(activationKey);
            renderer.render("/WEB-INF/facelets/email/email_validation.xhtml");

            FacesMessages
                    .instance()
                    .add("You will soon receive an email with a link to activate your email account change.");
        }
    }

    protected boolean isEmailAddressValid(String email) {
        HPerson person = personDAO.findByEmail(email);
        return person == null
                || person.getAccount().equals(authenticatedAccount);
    }

    public void changePassword() {
        if (isPasswordSet()
                && !identityManager.authenticate(
                authenticatedAccount.getUsername(), oldPassword)) {
            FacesMessages.instance().addToControl("oldPassword",
                    "Old password is incorrect, please check and try again.");
            return;
        }

        new RunAsOperation() {
            public void execute() {
                identityManager.changePassword(
                        authenticatedAccount.getUsername(), newPassword);
            }
        }.addRole("admin").run();

        FacesMessages.instance().add(
                "Your password has been successfully changed.");
    }

    public boolean isPasswordSet() {
        return authenticatedAccount.getPasswordHash() != null;
    }

    public List<HCredentials> getUserCredentials() {
        HAccount account = accountDAO.findById(authenticatedAccount.getId());
        return Lists.newArrayList(account.getCredentials());
    }

    public String getAccountUsername() {
        return authenticatedAccount.getUsername();
    }

    /**
     * Valid Types:
     * google, yahoo, fedora, openid for everything else
     */
    public String getCredentialsType(HCredentials credentials) {
        if( new GoogleOpenIdProvider().accepts(credentials.getUser()) ) {
            return "google";
        }
        else if( new FedoraOpenIdProvider().accepts(credentials.getUser()) ) {
            return "fedora";
        }
        else if( new YahooOpenIdProvider().accepts(credentials.getUser()) ) {
            return "yahoo";
        }
        else {
            return "openid";
        }
    }

    public String getCredentialsTypeDisplayName(String type) {
        if(type.equals("google"))
            return "Google";
        else if(type.equals("fedora"))
            return "Fedora";
        if(type.equals("yahoo"))
            return "Yahoo";
        if(type.equals("openid"))
            return "Open Id";
        else
            return "Unknown";
    }

    public void remove(HCredentials toRemove) {
        HAccount account =
                accountDAO.findById(authenticatedAccount.getId(), false);
        account.getCredentials().remove(toRemove);
        //userCredentials = new ArrayList<HCredentials>(account.getCredentials()); // Reload
        // the
        // credentials
        accountDAO.makePersistent(account);
        accountDAO.flush();
    }

    public void verifyCredentials(String providerTypeStr) {
        OpenIdProviderType providerType =
                OpenIdProviderType.valueOf(providerTypeStr);
        HOpenIdCredentials newCreds = new HOpenIdCredentials();
        newCreds.setAccount(authenticatedAccount);
        if( providerType == OpenIdProviderType.Generic ) {
            authenticationManager.openIdAuthenticate(openId, providerType,
                    new CredentialsCreationCallback(newCreds));
        }
        else {
            authenticationManager.openIdAuthenticate(providerType,
                    new CredentialsCreationCallback(newCreds));
        }
    }

    public boolean isApiKeyGenerated() {
        HAccount account =
                accountDAO.findById(authenticatedAccount.getId());

        return account.getApiKey() != null;
    }

    public String getAccountApiKey() {
        HAccount account =
                accountDAO.findById(authenticatedAccount.getId());
        return account.getApiKey();
    }

    public String getUrlKeyLabel() {
        return getKeyPrefix() + ".url=";
    }

    public String getApiKeyLabel() {
        return getKeyPrefix() + ".key=";
    }

    public String getUsernameKeyLabel() {
        return getKeyPrefix() + ".username=";
    }

    /*
     * Replace server name that contains '.' to '_'
     */
    private String getKeyPrefix() {
        ExternalContext context = javax.faces.context.FacesContext
                .getCurrentInstance().getExternalContext();
        HttpServletRequest request = (HttpServletRequest) context
                .getRequest();
        String serverName = request.getServerName();
        if (serverName == null) {
            return "";
        }
        return serverName.replace(".", "_");
    }

    public void regenerateApiKey() {
        HAccount account =
                accountDAO.findById(authenticatedAccount.getId());

        accountDAO.createApiKey(account);
        accountDAO.makePersistent(account);
        log.info("Reset API key for {}", account.getUsername());
    }

    public void updateProfile() {
        HPerson person =
                personDAO.findById(authenticatedAccount.getPerson().getId());
        person.setName( accountName );
        // Update the injected object as well.
        // TODO When more fields are added, we'll need a better solution
        authenticatedAccount.getPerson().setName( accountName );
        personDAO.makePersistent(person);
        FacesMessages.instance().add(
                msgs.get("jsf.dashboard.settings.profileUpdated.message"));
    }

    // TODO Cache this
    public List<HLocale> getUserLanguageTeams() {
        List<HLocale> localeList =
                languageTeamServiceImpl.getLanguageMemberships(
                    authenticatedAccount.getUsername());
        Collections.sort(localeList, ComparatorUtil.LOCALE_COMPARATOR);
        return localeList;
    }

    @Transactional
    public void leaveLanguageTeam(String localeId) {
        languageTeamServiceImpl.leaveLanguageTeam(localeId,
                authenticatedAccount.getPerson().getId());
        FacesMessages.instance().add(
                msgs.format("jsf.dashboard.settings.leaveLangTeam.message",
                        localeId));
    }

    /**
     * Callback for credential creation.
     */
    private static class CredentialsCreationCallback implements
            OpenIdAuthCallback, Serializable {
        private static final long serialVersionUID = 1L;
        private HCredentials newCredentials;

        private CredentialsCreationCallback(HCredentials newCredentials) {
            this.newCredentials = newCredentials;
        }

        @Override
        public void afterOpenIdAuth(OpenIdAuthenticationResult result) {
            // Save the credentials after a successful authentication
            if (result.isAuthenticated()) {
                this.newCredentials.setUser(result.getAuthenticatedId());
                this.newCredentials.setEmail(result.getEmail());
                // NB: Seam component injection won't work on callbacks
                EntityManager em =
                        ServiceLocator.instance().getEntityManager();
                CredentialsDAO credentialsDAO =
                        ServiceLocator.instance().getInstance(
                                CredentialsDAO.class);

                Conversation.instance().begin(true, false); // (To retain
                // messages)
                FacesMessages.instance().clear();

                if (credentialsDAO.findByUser(result.getAuthenticatedId()) != null) {
                    FacesMessages.instance().add(ERROR,
                            "jsf.identities.invalid.Duplicate", null,
                            "Duplicate identity",
                            "This Identity is already in use.");
                } else {
                    em.persist(this.newCredentials);
                    FacesMessages
                            .instance()
                            .add(INFO, "jsf.identities.IdentityAdded", null,
                                    "Identity Added",
                                    "Your new identity has been added to this account.");
                }
            }
        }

        @Override
        public String getRedirectToUrl() {
            return "/dashboard/settings?cid="
                    + Conversation.instance().getId(); // keep the same
            // conversation
        }
    }
}