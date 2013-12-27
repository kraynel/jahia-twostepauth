First warning  
===========  
 
 
This module is experimental. It modifies the defaults Jahia authentication process and should not be used if you do not fully understand the concepts behind two factor authentication.  
 
 
It works well with any mobile authenticator application based on the [RFC 6238](https://tools.ietf.org/html/rfc6238) :  
 
- for [iOS](https://itunes.apple.com/fr/app/google-authenticator/id388497605) and [Android](https://play.google.com/store/apps/details?id=com.google.android.apps.authenticator2) you can use the Google Authenticator app.  
 
- for [Windows Phone](http://www.windowsphone.com/en-us/store/app/authenticator/e7994dbc-2336-4950-91ba-ca22d653759b), you can use the Authenticator app developped by Microsoft.   
 
 
You can add a two factor authentication factor on any existing authentication valve.  
 
Users use the same token for all sites on an instance of Jahia. During the enrollment process, they are given a secret recovery password, which is only usable once. Once used, you should ask the user to generate a new one.  
 
---
 
How to install ?  
=============  
 
The installation process is in several steps.  
 
- First, you install the module itself. Just drop the war into your shared_modules directory. It registers an authentication wrapper class, which adds the second authentication factor to any existing authentication valve. The default wrapped valve is the JCR Authentication Valve.
 
- You then have to install the updated 401 error page. You can download the latest one [here](#).
 
- You can now add the provided enrollement module to the system site (and configure your [issuer prefix](http://code.google.com/p/google-authenticator/wiki/KeyUriFormat#Label)).
    - by running [the provided groovy script](#) in the [groovy console](http://localhost:8080/tools/groovyConsole.jsp), which add all the dependencies between your system site and the module
	- or by going into studio mode and manually adding the ```jnt:configureTwoFactorAuth``` component into the system site (I add it below the other personnal settings in the base/user-base/user-edit-details page, but it is totally up to you.
	
- Finally, modify your login module to support two factor auth. The module provides a specific view for the login module used in the space acme template, if you want to test it.
 
---  
 
F.A.Q.  
====== 
 
- How do I activate/deactivate two factor authentication on my account ?  
 
> Just login, go to [/start](http://localhost:8080/start), my settings, and click activate two factor auth. This setting will activate two factor auth for all sites.  
 
- How to use a two factor auth with my custom login form ?  
 
> When requesting the token for a user, the ```login_error``` parameter will be ```ask_totp```. You have to ask the token value in an input named ```totp``` and post it to the login url.  
 
- The two factor auth only works for my JCR users, is that normal?  
 
> The two factor authentication is not an authentication valve, it is a wrapper for a valve. By default, the two factor wrapper is around the default JCR authentication valve, but you can add it around any authentication valve. Just create a spring file in your module, and add the following code : 
 
```xml 
<bean id="LoginEngineAuthValve" class="org.jahia.params.valves.LoginEngineAuthValveImpl"> 
        <property name="cookieAuthConfig" ref="cookieAuthConfig"/> 
        <property name="fireLoginEvent" value="${fireLoginEvent:false}"/> 
        <property name="preserveSessionAttributes" value="${preserveSessionAttributesOnLogin:}" /> 
</bean> 
 
<bean id="authPipeline" class="org.jahia.pipelines.impl.GenericPipeline" init-method="initialize"> 
        <property name="name" value="authPipeline" /> 
        <property name="valves"> 
            <list> 
                <bean id="HttpBasicAuthValve" class="org.jahia.params.valves.HttpBasicAuthValveImpl" /> 
                <bean id="TokenAuthValve" class="org.jahia.params.valves.TokenAuthValveImpl" /> 
                <bean id="TwoFactorLoginEngineAuthValve" class="org.jahia.params.valves.custom.LoginTwoFactorWrapperImpl"> 
                    <property name="wrappedValve" ref="LoginEngineAuthValve" /> 
                </bean> 
                <bean id="SessionAuthValve" class="org.jahia.params.valves.SessionAuthValveImpl" /> 
                <bean id="CookieAuthValve" class="org.jahia.params.valves.CookieAuthValveImpl"> 
                    <property name="cookieAuthConfig" ref="cookieAuthConfig"/> 
                </bean> 
                <bean id="ContainerAuthValve" class="org.jahia.params.valves.ContainerAuthValveImpl"> 
                    <property name="enabled" value="${auth.container.enabled}"/> 
                </bean> 
            </list> 
        </property> 
    </bean> 
``` 
 
- Can I check the token in another critical part of my application ?   
 
> Absolutely, you have to post your password and token to the action called "checkToken". It Will return a 200 code with a json result. You can use that behaviour by chaining actions for instance.  
 
- How Can I uninstall the module ?  
 
> Jahia modules are not meant to be uninstalled, but you can easily disable it. You can override the authentication valve pipeline (see [jahia documentation](http://www.jahia.com/community/documentation/security/authenticationValves.html)). You can also delete all node of type ```jnt:enableTwoFactorAuth```, which would delete all your user enrollments.