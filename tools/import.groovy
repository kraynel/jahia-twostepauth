import java.util.* 
import javax.jcr.*

import org.jahia.registries.ServicesRegistry
import org.jahia.services.content.*
 
def isOk = JCRTemplate.getInstance().doExecuteWithSystemSession(new JCRCallback<Boolean>() {
    public Object doInJCR(JCRSessionWrapper session) throws RepositoryException {
                
                ServicesRegistry.getInstance().getJahiaTemplateManagerService().deployModule("/templateSets/totp", "/sites/systemsite", "root");
		JCRPropertyWrapper jProps = session.getNode("/templateSets/templates-system").getProperty("j:dependencies");
		jProps.addValue("totp");
		session.save();
		
		JCRNodeWrapper nodeWrapper = session.getNode("/templateSets/templates-system/templates/base/user-base/user-edit-details/pagecontent/pagecontent");
		JCRNodeWrapper configureTwoFactorAuth = nodeWrapper.addNode("configureTwoFactorAuth", "jnt:configureTwoFactorAuth");
		configureTwoFactorAuth.setProperty("providerName", "Jahia");
		session.save();

		ServicesRegistry.getInstance().getJahiaTemplateManagerService().deployModule("/templateSets/templates-system", "/sites/systemsite", "root");
		
		return true;
    }
});
 