package org.jahia.params.valves.custom;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.util.Calendar;
import java.util.Properties;
import java.util.Set;

import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.apache.commons.lang.StringUtils;
import org.jahia.params.valves.AuthValveContext;
import org.jahia.params.valves.BaseAuthValve;
import org.jahia.params.valves.CookieAuthConfig;
import org.jahia.params.valves.CookieAuthValveImpl;
import org.jahia.params.valves.LoginEngineAuthValveImpl;
import org.jahia.pipelines.PipelineException;
import org.jahia.pipelines.valves.ValveContext;
import org.jahia.registries.ServicesRegistry;
import org.jahia.security.TOTP;
import org.jahia.services.content.JCRCallback;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.JCRTemplate;
import org.jahia.services.usermanager.JahiaUser;
import org.slf4j.Logger;

public class LoginTwoFactorWrapperImpl extends BaseAuthValve implements ValveContext {

	private BaseAuthValve wrappedValve;
	private CookieAuthConfig cookieAuthConfig;

    private static final transient Logger logger = org.slf4j.LoggerFactory.getLogger(LoginTwoFactorWrapperImpl.class);

    public static final String TWO_FACTOR_AUTH_ENABLED_PARAM = "enableTwoFactorAuth";
    public static final String USER_PASS_VERIFIED = "password_already_verified";
    public static final String CHECK_TOTP = "ask_totp";
  
    public CookieAuthConfig getCookieAuthConfig() {
		return cookieAuthConfig;
	}
    
    public void setCookieAuthConfig(CookieAuthConfig cookieAuthConfig) {
		this.cookieAuthConfig = cookieAuthConfig;
	}
    
	public void invoke(Object context, ValveContext valveContext) throws PipelineException {
		if (!isEnabled()) {
            valveContext.invokeNext(context);
            return;
        }
		
		JahiaUser theUser = null;
		final AuthValveContext authContext = (AuthValveContext) context;
        final HttpServletRequest httpServletRequest = authContext.getRequest();

        if (!isLoginTOTPRequested(httpServletRequest)) {
        	DummyValveContext dummyContext = new DummyValveContext();
        	wrappedValve.invoke(authContext, dummyContext);
        	if(dummyContext.getNextValveisInvoked()){
        		// The wrapped valve did not authenticated the user, try the next valve
        		valveContext.invokeNext(context);
                return;
        	} else if(LoginEngineAuthValveImpl.OK.equals(httpServletRequest.getAttribute(LoginEngineAuthValveImpl.VALVE_RESULT))) {
        		logger.debug("Login TOTP not requested yet, login password success, cancel login and request TOTP");
        		
        		// The wrapped valve did authenticate the user
        		theUser = authContext.getSessionFactory().getCurrentUser();
        		boolean isTwoFactorEnabled = false;
        		
        		try {
        			isTwoFactorEnabled = JCRTemplate.getInstance().doExecuteWithSystemSession(theUser.getUsername(), new JCRCallback<Boolean>() {
						public Boolean doInJCR(JCRSessionWrapper session) throws RepositoryException {
							try {
								JCRNodeWrapper totpNode = session.getNode(session.getUser().getLocalPath() + "/totp");
								return totpNode.getProperty(TWO_FACTOR_AUTH_ENABLED_PARAM).getBoolean();
							} catch(PathNotFoundException e){
								return false;
							} 
						}
					});
				} catch (RepositoryException e) {
				}
        		
     			if(isTwoFactorEnabled) {
     				logger.debug("Login ok but TOTP enabled, wait for code");
     		           
     				HttpSession session = httpServletRequest.getSession(true);
     				session.setAttribute(USER_PASS_VERIFIED, theUser.getUsername());
     				
     				// The user is not yet fully logged, remove all parameters which could log the user in
     				authContext.getSessionFactory().setCurrentUser(null);
     				String useCookie = httpServletRequest.getParameter(LoginEngineAuthValveImpl.USE_COOKIE);
     		        if ((useCookie != null) && ("on".equals(useCookie))){
     		        	theUser.removeProperty(cookieAuthConfig.getUserPropertyName());
     		        	Cookie authCookie = new Cookie(cookieAuthConfig.getCookieName(), "");
     		        	authCookie.setPath(StringUtils.isNotEmpty(httpServletRequest.getContextPath()) ?
     		        			httpServletRequest.getContextPath() : "/");
     		        	authCookie.setMaxAge(0);
     		        	session.setAttribute(LoginEngineAuthValveImpl.USE_COOKIE, LoginEngineAuthValveImpl.OK);
     		        }
     		        
     				httpServletRequest.setAttribute(LoginEngineAuthValveImpl.VALVE_RESULT, CHECK_TOTP);
     				return;
     			}
        		
        	}
        } else {
        	HttpSession session = httpServletRequest.getSession(false);
			if(session == null){
				valveContext.invokeNext(context);
				return;
			}
			
			String username = (String) session.getAttribute(USER_PASS_VERIFIED);
			if(username != null) {
				theUser = ServicesRegistry.getInstance().getJahiaUserManagerService().lookupUser(username);
			}
			
			boolean isValid = false;
			if(theUser != null){
				final String totp = httpServletRequest.getParameter("totp");
				if(null == totp || "".equals(totp)) {
					valveContext.invokeNext(context);
					return;
				}

				
				String[] jcrProperties = null;
				try {
        			jcrProperties = JCRTemplate.getInstance().doExecuteWithSystemSession(theUser.getUsername(), new JCRCallback<String[]>() {
						public String[] doInJCR(JCRSessionWrapper session) throws RepositoryException {
							try {
								JCRNodeWrapper totpNode = session.getNode(session.getUser().getLocalPath() + "/totp");
								String secret =  totpNode.getPropertyAsString("secretTotp");
								String emergency =  totpNode.getPropertyAsString("emergencyCode");
								
								return new String[] {secret, emergency};
							} catch(PathNotFoundException e){
							} 
							
							return null;
						}
					});
				} catch (RepositoryException e) {
				}
				
				if(jcrProperties == null || jcrProperties.length != 2) {
					valveContext.invokeNext(context);
					return;
				}
				
				String secret = jcrProperties[0];
				String emergency = jcrProperties[1];
				
	            try {
					isValid = TOTP.check_code(secret, Long.parseLong(totp), Calendar.getInstance().getTimeInMillis() / 1000 / 30);
					if(isValid){
						authContext.getSessionFactory().setCurrentUser(theUser);
						trySetCookie(authContext, cookieAuthConfig, theUser);
						
						httpServletRequest.setAttribute(LoginEngineAuthValveImpl.VALVE_RESULT, LoginEngineAuthValveImpl.OK);
						return;
					} else {
						valveContext.invokeNext(context);
						return;
					}
					
				} catch (InvalidKeyException e) {
					logger.error(e.getMessage(),e);
				} catch (NumberFormatException e) {
					
					if(totp.equalsIgnoreCase(emergency)){
						
						try {
							JCRTemplate.getInstance().doExecuteWithSystemSession(theUser.getUsername(), new JCRCallback<Boolean>() {
								public Boolean doInJCR(JCRSessionWrapper session) throws RepositoryException {
									try {
										JCRNodeWrapper totpNode = session.getNode(session.getUser().getLocalPath() + "/totp");
										totpNode.setProperty("emergencyCode", (String) null);
										session.save();
										return true;
									} catch(PathNotFoundException e){
									} 
									
									return false;
								}
							});
						} catch (RepositoryException e1) {
						}
						
						
						authContext.getSessionFactory().setCurrentUser(theUser);
						httpServletRequest.setAttribute(LoginEngineAuthValveImpl.VALVE_RESULT, LoginEngineAuthValveImpl.OK);
						return;
					}
				} catch (NoSuchAlgorithmException e) {
					logger.error(e.getMessage(),e);
				}	
			}
			
        }
        valveContext.invokeNext(context);
	}
	
	private void trySetCookie(AuthValveContext authContext, CookieAuthConfig cookieAuthConfig, JahiaUser theUser) {
		HttpServletRequest httpServletRequest = authContext.getRequest();
		
		HttpSession session = httpServletRequest.getSession(false);
		if(session == null) return;

		String useCookie = (String) session.getAttribute(LoginEngineAuthValveImpl.USE_COOKIE);
        if ((useCookie != null) && (LoginEngineAuthValveImpl.OK.equals(useCookie))) {
            // the user has indicated he wants to use cookie authentication
            // now let's create a random identifier to store in the cookie.
            String cookieUserKey = null;
            // now let's look for a free random cookie value key.
            while (cookieUserKey == null) {
                cookieUserKey = CookieAuthValveImpl.generateRandomString(cookieAuthConfig.getIdLength());
                Properties searchCriterias = new Properties();
                searchCriterias.setProperty(cookieAuthConfig.getUserPropertyName(), cookieUserKey);
                Set<Principal> foundUsers =
                        ServicesRegistry.getInstance().getJahiaUserManagerService().searchUsers(searchCriterias);
                if (foundUsers.size() > 0) {
                    cookieUserKey = null;
                }
            }
            // let's save the identifier for the user in the database
            theUser.setProperty(cookieAuthConfig.getUserPropertyName(), cookieUserKey);
            // now let's save the same identifier in the cookie.
            Cookie authCookie = new Cookie(cookieAuthConfig.getCookieName(), cookieUserKey);
            authCookie.setPath(StringUtils.isNotEmpty(httpServletRequest.getContextPath()) ?
                    httpServletRequest.getContextPath() : "/");
            authCookie.setMaxAge(cookieAuthConfig.getMaxAgeInSeconds());
            authContext.getResponse().addCookie(authCookie);
        }
	}
	
	 protected boolean isLoginTOTPRequested(HttpServletRequest request) {
	        return request.getParameter("totp") != null;
	    }

	public void invokeNext(Object context) throws PipelineException {
		// WrappedValve tried to invoke next valve : auth failed
		
		final TwoFactorAuthValveContext authContext = (TwoFactorAuthValveContext) context;
		authContext.getValveContext().invokeNext(authContext);
	}

	public BaseAuthValve getWrappedValve() {
		return wrappedValve;
	}
	
	public void setWrappedValve(BaseAuthValve wrappedValve) {
		this.wrappedValve = wrappedValve;
	}
}
