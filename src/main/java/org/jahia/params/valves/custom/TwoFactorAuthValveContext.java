package org.jahia.params.valves.custom;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.jahia.params.valves.AuthValveContext;
import org.jahia.pipelines.valves.ValveContext;
import org.jahia.services.content.JCRSessionFactory;

public class TwoFactorAuthValveContext extends AuthValveContext {
	private HttpServletRequest request;
    private HttpServletResponse response;
    private JCRSessionFactory sessionFactory;
    private ValveContext valveContext;
    
    public TwoFactorAuthValveContext(HttpServletRequest request, HttpServletResponse response, JCRSessionFactory sessionFactory) {
        super(request, response, sessionFactory);
    }

    public HttpServletRequest getRequest() {
        return request;
    }

    public HttpServletResponse getResponse() {
        return response;
    }

    public JCRSessionFactory getSessionFactory() {
        return sessionFactory;
    }
    
    public ValveContext getValveContext() {
		return valveContext;
	}

    public void setValveContext(ValveContext valveContext) {
		this.valveContext = valveContext;
	}
}
