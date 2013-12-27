<%@ page import="org.apache.commons.lang.StringUtils" %>
<%@ page import="org.jahia.services.content.JCRNodeWrapper" %>
<%@ page import="javax.jcr.RepositoryException" %>
<%@ page import="javax.jcr.Value" %>
<%@ page import="java.util.HashSet" %>
<%@ page import="org.jahia.services.content.JCRPropertyWrapper" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="jcr" uri="http://www.jahia.org/tags/jcr" %>
<%@ taglib prefix="utility" uri="http://www.jahia.org/tags/utilityLib" %>
<%@ taglib prefix="template" uri="http://www.jahia.org/tags/templateLib" %>
<%@ taglib prefix="uiComponents" uri="http://www.jahia.org/tags/uiComponentsLib" %>
<%@ taglib prefix="functions" uri="http://www.jahia.org/tags/functions" %>
<%@ taglib prefix="user" uri="http://www.jahia.org/tags/user" %>

<template:addResources type="css" resources="userProfile.css" />
<template:addResources type="css" resources="twofactorauth.css"/>

<template:addResources type="javascript" resources="jquery.min.js"/>

<jcr:node path="${currentUser.localPath}/totp" var="totpNode" />

<template:addCacheDependency node="${totpNode}"/>


	<ul class="user-profile-list">
		<li>
           <span class="label"><fmt:message key="label.twofactor"/></span>
           <c:choose>
           <c:when test="${!empty totpNode.properties.enableTwoFactorAuth && totpNode.properties.enableTwoFactorAuth.boolean}">
           			<c:set var="statusTotp" value="label.enabled" />
           			<c:set var="statusTechTotp" value="Disable" />
           			<c:set var="actionTotp" value="label.clickToDisable" />
           		</c:when>
           		<c:otherwise>
					<c:set var="statusTotp" value="label.disabled" />
					<c:set var="statusTechTotp" value="Enable" />
           			<c:set var="actionTotp" value="label.clickToEnable" />
           		</c:otherwise>
     		</c:choose>
     	   <span id="totpStatus"><fmt:message key="${statusTotp}"/></span>
           <span id="actionTotp${statusTechTotp}" class="inline-editable"><fmt:message key="${actionTotp}"/></span>
       </li>
       
       <div class="twoFactorAuth twoFactorAuthDisable" style="display:none">
       	<p><fmt:message key="configureTwoFactorAuth.disable.warning.label"/><p>
       	<p><fmt:message key="configureTwoFactorAuth.ask.password.label" /></p>
       	<p class="badPassword" style="color:red;display:none;"><fmt:message key="configureTwoFactorAuth.bad.password.label" /></p>
       	<p><input type="password" name="password" id="totpPasswordDisable" placeholder="<fmt:message key="configureTwoFactorAuth.password.label" />"/></p>
       </div>
       
       <div class="twoFactorAuth step1" style="display:none">
       	<p><fmt:message key="configureTwoFactorAuth.enable.warning.label"/><p>
       	<p><fmt:message key="configureTwoFactorAuth.phone.application.label"/></p>
       	<p><fmt:message key="configureTwoFactorAuth.ask.password.label" /></p>
       	<p class="badPassword" style="color:red;display:none;"><fmt:message key="configureTwoFactorAuth.bad.password.label" /></p>
       	<p><input type="password" name="password" id="totpPassword" placeholder="<fmt:message key="configureTwoFactorAuth.password.label" />"/></p>
       </div>
       
       <div class="twoFactorAuth step2" style="display:none">
       	<p><fmt:message key="configureTwoFactorAuth.qr.scan.label" /> <span class="inline-editable" id="toggleQRCode"><fmt:message key="configureTwoFactorAuth.direct.code.input.label" /></span></p>
       	<img src="" id="qrCodeSecret" alt="<fmt:message key="configureTwoFactorAuth.qr.alt.label" />" />
       	<p style="display:none" id="hiddenToken" ><input type="text" readonly></p> 
       	
       </div>
       
       <div class="twoFactorAuth step3" style="display:none">
       <p><fmt:message key="configureTwoFactorAuth.code.checking.label" /></p>
       <p class="goodToken" style="display:none;color:green;"><fmt:message key="configureTwoFactorAuth.correct.code.label" /></p>
       <p class="badToken" style="display:none;color:red;"><fmt:message key="configureTwoFactorAuth.incorrect.code.label" /></p>
       	<input type="text" id="checkTokenInput" placeholder="<fmt:message key="label.totp" />"/>
       	<button id="checkToken"><fmt:message key="configureTwoFactorAuth.check.label" /></button>
       <p><fmt:message key="configureTwoFactorAuth.emergency.code.label" /></p>
       <p><fmt:message key="configureTwoFactorAuth.emergency.code.info.label" /></p>
       		<input type="text" id="emergencyCode" readonly />
       </div>
       
       
       
        <script type="text/javascript">

            $(document).ready(function() {
				
            	$('.twoFactorAuth.step3 button').button();
            	$('#actionTotpDisable').click(function(){
                	
	            	$('.twoFactorAuthDisable').dialog({
						modal: true,
						 buttons: {
							 '<fmt:message key="configureTwoFactorAuth.button.continue.label" />': function() {
								 $.ajax({
					            		'url': '<c:url value="${url.baseLive}${currentNode.path}.twoFactorEnabler.do"/>',
					            		'data' : {"password" : $('#totpPasswordDisable').val() },
					            		'type': 'POST',
					            		'dataType': 'json',
					            		'success': function(data){
					            			window.location.reload();
					            			
					            		},
					            		'error': function(){
					            			$('.twoFactorAuthDisable .badPassword').show();
					            		}
				            		});
							 	
							 },
							 '<fmt:message key="configureTwoFactorAuth.button.cancel.label" />': function() {
							 	$( this ).dialog( 'close' );
							 	$('#totpPasswordDisable').val('');
							 }
						 }
					});
            	});
            	
            	
            	$('#actionTotpEnable').click(function(){
            	
	            	$('.twoFactorAuth.step1').dialog({
						modal: true,
						 buttons: {
							 '<fmt:message key="configureTwoFactorAuth.button.continue.label" />': function() {
								 generateSecret(this);
							 	
							 },
							 '<fmt:message key="configureTwoFactorAuth.button.cancel.label" />': function() {
							 	$( this ).dialog( 'close' );
							 	$('#totpPasswordDisable').val('');
							 }
						 }
					});
            	});
            	
            	$('#toggleQRCode').click(function(){
            		$('#qrCodeSecret').toggle();
        			$('#hiddenToken').toggle();
            	});
            	
            	$('#checkToken').click(function(){
            		$.ajax({
	            		'url': '<c:url value="${url.baseLive}${currentNode.path}.twoFactorCheckCode.do"/>',
	            		'data' : {'password' : $('#totpPassword').val(), 'checktotp' : $('#checkTokenInput').val() },
	            		'type': 'POST',
	            		'dataType': 'json',
	            		'success': function(data){
	            			$('.twoFactorAuth.step3 .goodToken').show();
	            			$('.twoFactorAuth.step3 .badToken').hide();
	            			
	            		},
	            		'error': function(){
	            			$('.twoFactorAuth.step3 .goodToken').hide();
	            			$('.twoFactorAuth.step3 .badToken').show();
	            		}
            		});
            	});
            });

            
            function generateSecret(oldDialog){
            	$.ajax({
	            		'url': '<c:url value="${url.baseLive}${currentNode.path}.twoFactorGenSecret.do"/>',
	            		'data' : {'password' : $('#totpPassword').val()},
	            		'type': 'POST',
	            		'dataType': 'json',
	            		'success': function(data){
	            			$('#qrCodeSecret').attr('src', getQRCodeUrl(data.secret));
	            			$('#hiddenToken input').val(data.secret);
	            			$('#emergencyCode').val(data.emergency);
	            			
	            			$(oldDialog).dialog( 'close' );
	            			$('.twoFactorAuth.step1 .badPassword').hide();
	            			
	            			$('.twoFactorAuth.step2').dialog({
	            				
	        					modal: true,
	        					 buttons: {
	        						 '<fmt:message key="configureTwoFactorAuth.button.check.label" />': function() {
	        							 $( this ).dialog( 'close' );
	        							 openCheckCode();
	        						 },
	        						 '<fmt:message key="configureTwoFactorAuth.button.cancel.label" />': function() {
	        						 	$( this ).dialog( 'close' );
	        						 	$('#totpPasswordDisable').val('');
	        						 }
	        					 }
	        				});
	            		},
	            		'error': function(){
	            			$('.twoFactorAuth.step1 .badPassword').show();
	            		}
            		});
            }
            
            function openCheckCode(oldDialog){
            	$('.twoFactorAuth.step3').dialog({
					modal: true,
					 buttons: {
						 '<fmt:message key="configureTwoFactorAuth.button.activate.label" />': function() {
							 $.ajax({
				            		'url': '<c:url value="${url.baseLive}${currentNode.path}.twoFactorEnabler.do"/>',
				            		'data' : {'password' : $('#totpPassword').val()},
				            		'type': 'POST',
				            		'dataType': 'json',
				            		'success': function(data){
									 	window.location.reload();
				            		}
							 });
						 },
						 '<fmt:message key="configureTwoFactorAuth.button.cancel.label" />': function() {
						 	$( this ).dialog( 'close' );
						 	$('#totpPasswordDisable').val('');
						 }
					 }
				});
            }
            
            function getQRCodeUrl(secret) {
            	var label = '${currentNode.properties.providerName.string}:${currentUser.username}'; 
            	return (('https:' == document.location.protocol) ? 'https' : 'http') +'://chart.googleapis.com/chart?chs=200x200&chld=M|0&cht=qr&chl=otpauth://totp/' + label + '?secret='+ secret;
            }
        </script>
</ul>

