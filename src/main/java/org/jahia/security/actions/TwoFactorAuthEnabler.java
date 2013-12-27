package org.jahia.security.actions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.httpclient.HttpStatus;
import org.jahia.api.Constants;
import org.jahia.bin.Action;
import org.jahia.bin.ActionResult;
import org.jahia.services.content.JCRCallback;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRPublicationService;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.JCRTemplate;
import org.jahia.services.render.RenderContext;
import org.jahia.services.render.Resource;
import org.jahia.services.render.URLResolver;
import org.jahia.services.usermanager.JahiaUser;
import org.json.JSONObject;

public class TwoFactorAuthEnabler extends Action {
	
	private static final String ENABLE_TWO_FACTOR_AUTH = "enableTwoFactorAuth";
	private static final String SECRET_TOTP = "secretTotp";
	
	private static final String PARAMETER_PASSWORD = "password";
	
	private JCRTemplate jcrTemplate;

	public JCRTemplate getJcrTemplate() {
		return jcrTemplate;
	}
	
	public void setJcrTemplate(JCRTemplate jcrTemplate) {
		this.jcrTemplate = jcrTemplate;
	}
	
	@Override
	public ActionResult doExecute(HttpServletRequest req,
			RenderContext renderContext, Resource resource,
			JCRSessionWrapper session, Map<String, List<String>> parameters,
			URLResolver urlResolver) throws Exception {
		
		String password = getRequiredParameter(parameters, PARAMETER_PASSWORD);
		final JahiaUser myUser = renderContext.getUser();
		if(! myUser.verifyPassword(password)) return new ActionResult(HttpStatus.SC_FORBIDDEN);
		
		try {
			JCRNodeWrapper totpNode = session.getNode(myUser.getLocalPath() + "/totp");
			final boolean isEnabled = totpNode.getProperty(ENABLE_TWO_FACTOR_AUTH).getBoolean();
		
			if(!isEnabled && totpNode.getProperty(SECRET_TOTP) == null) return new ActionResult(HttpStatus.SC_EXPECTATION_FAILED);
			
			String status = jcrTemplate.doExecuteWithUserSession(renderContext.getUser().getUsername(),Constants.EDIT_WORKSPACE, new JCRCallback<String>() {
		        public String doInJCR(JCRSessionWrapper session) throws RepositoryException {
		        	JCRNodeWrapper totpNodeEdit = session.getNode(myUser.getLocalPath() + "/totp");
		        	if(isEnabled) {
		        		totpNodeEdit.setProperty(ENABLE_TWO_FACTOR_AUTH, false);
		        		session.save();
		        		return "disabled";
		        	} else{
		        		totpNodeEdit.setProperty(ENABLE_TWO_FACTOR_AUTH, true);
		        		session.save();
		        		return "enabled";
		        	}
		        }
		    });
			JCRPublicationService.getInstance().publish(Arrays.asList(new String[] { totpNode.getIdentifier() }), Constants.EDIT_WORKSPACE, Constants.LIVE_WORKSPACE, new ArrayList<String>());
    	    
			JSONObject res = new JSONObject();
			res.put("status", status);
			
			return new ActionResult(ActionResult.OK_JSON.getResultCode(), (String) null, res);

		} catch(PathNotFoundException e){
		}
		
		return ActionResult.INTERNAL_ERROR;
	}

}
