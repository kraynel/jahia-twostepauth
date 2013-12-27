package org.jahia.security.actions;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.codec.binary.Base32;
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
import org.json.JSONException;
import org.json.JSONObject;

public class GenerateSecret extends Action {

	private static final String JNT_ENABLE_TWO_FACTOR_AUTH = "jnt:enableTwoFactorAuth";

	private static final String PARAMETER_PASSWORD = "password";
	private static final String ENABLE_TWO_FACTOR_AUTH = "enableTwoFactorAuth";
	private static final String SECRET_TOTP = "secretTotp";
	private static final String EMERGENCY_CODE = "emergencyCode";
	
	private int secretByteLength;
	private JCRTemplate jcrTemplate;
	
	public int getSecretByteLength() {
		return secretByteLength;
	}
	
	public void setSecretByteLength(int secretByteLength) {
		this.secretByteLength = secretByteLength;
	}
	
	public JCRTemplate getJcrTemplate() {
		return jcrTemplate;
	}
	
	public void setJcrTemplate(JCRTemplate jcrTemplate) {
		this.jcrTemplate = jcrTemplate;
	}
	
	@Override
	public ActionResult doExecute(HttpServletRequest req,
			final RenderContext renderContext, Resource resource,
			JCRSessionWrapper session, final Map<String, List<String>> parameters,
			URLResolver urlResolver) throws Exception {
		
		return jcrTemplate.doExecuteWithUserSession(renderContext.getUser().getUsername(),Constants.EDIT_WORKSPACE, new JCRCallback<ActionResult>() {
	        public ActionResult doInJCR(JCRSessionWrapper session) throws RepositoryException {
	        	String password = getRequiredParameter(parameters, PARAMETER_PASSWORD);
	    		JahiaUser myUser = renderContext.getUser();
	    		
	    		if(! myUser.verifyPassword(password)) return new ActionResult(HttpStatus.SC_FORBIDDEN);

	    		JCRNodeWrapper totpNode = null;
	    		try {
	    			totpNode = session.getNode(myUser.getLocalPath() + "/totp");
	    		} catch(PathNotFoundException e){
	    			totpNode = session.getNode(myUser.getLocalPath()).addNode("totp", JNT_ENABLE_TWO_FACTOR_AUTH);
	    			totpNode.setProperty(ENABLE_TWO_FACTOR_AUTH, false);
	    		}
	    		
	    		SecureRandom random = new SecureRandom();
	    	    byte bytes[] = new byte[secretByteLength];
	    	    byte bytesEmergency[] = new byte[10];
	    	    
	    	    random.nextBytes(bytes);
	    	    random.nextBytes(bytesEmergency);
	    		
	    	    Base32 codec = new Base32();
	    	    String secret = codec.encodeAsString(bytes);
	    	    String emergencySecret = codec.encodeAsString(bytesEmergency);
	    		emergencySecret = emergencySecret.substring(emergencySecret.length() - 8).replaceAll("(.{4})(?!$)", "$1-");
	    	    
	    		totpNode.setProperty(SECRET_TOTP, secret);
	    		totpNode.setProperty(EMERGENCY_CODE, emergencySecret);
	    	    session.save();
	    	    
	    	    JCRPublicationService.getInstance().publish(Arrays.asList(new String[] { totpNode.getIdentifier() }), Constants.EDIT_WORKSPACE, Constants.LIVE_WORKSPACE, new ArrayList<String>());
	    	    try {
		    	    JSONObject res = new JSONObject();
		    		res.put("secret", secret);
		    		res.put("emergency", emergencySecret);
		    		
		    		return new ActionResult(ActionResult.OK_JSON.getResultCode(), (String) null, res);
	    	    } catch(JSONException e){
	    	    	
	    	    }
	    	    
	            return null;
	        }
	    });
		
		
		
	}

}
