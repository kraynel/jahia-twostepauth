package org.jahia.security.actions;

import java.util.Calendar;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.httpclient.HttpStatus;
import org.jahia.bin.Action;
import org.jahia.bin.ActionResult;
import org.jahia.security.TOTP;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.render.RenderContext;
import org.jahia.services.render.Resource;
import org.jahia.services.render.URLResolver;
import org.jahia.services.usermanager.JahiaUser;
import org.json.JSONObject;

public class CheckCode extends Action {

	private static final String PARAMETER_TOTP = "checktotp";
	private static final String PARAMETER_PASSWORD = "password";
	private static final String SECRET_TOTP = "secretTotp";

	@Override
	public ActionResult doExecute(HttpServletRequest req,
			RenderContext renderContext, Resource resource,
			JCRSessionWrapper session, Map<String, List<String>> parameters,
			URLResolver urlResolver) throws Exception {
		
		String password = getRequiredParameter(parameters, PARAMETER_PASSWORD);
		String totp = getRequiredParameter(parameters, PARAMETER_TOTP);
		
		JahiaUser myUser = renderContext.getUser();
		if(! myUser.verifyPassword(password)) return new ActionResult(HttpStatus.SC_FORBIDDEN);
		
	    String secret = session.getNode(myUser.getLocalPath() + "/totp").getPropertyAsString(SECRET_TOTP);
	    
	    if(secret == null) {
	    	JSONObject res = new JSONObject();
			res.put("status", "not_enabled");
			return new ActionResult(ActionResult.OK_JSON.getResultCode(), (String) null, res);
	    }
	    
		boolean result = TOTP.check_code(secret, Long.parseLong(totp), Calendar.getInstance().getTimeInMillis() / 1000 / 30);
		
		JSONObject res = new JSONObject();
		res.put("isCodeOk", result);
		
		return new ActionResult(ActionResult.OK_JSON.getResultCode(), (String) null, res);
	}

}
