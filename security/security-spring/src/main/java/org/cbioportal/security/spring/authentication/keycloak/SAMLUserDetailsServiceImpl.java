/*
 * Copyright (c) 2016 The Hyve B.V.
 * This code is licensed under the GNU Affero General Public License (AGPL),
 * version 3, or (at your option) any later version.
 */

/*
 * This file is part of cBioPortal.
 *
 * cBioPortal is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package org.cbioportal.security.spring.authentication.keycloak;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.cbioportal.security.spring.authentication.PortalUserDetails;

import org.opensaml.saml2.core.Attribute;

import org.opensaml.xml.XMLObject;
import org.opensaml.xml.schema.XSString;
import org.opensaml.xml.schema.impl.XSAnyImpl;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.saml.SAMLCredential;
import org.springframework.security.saml.userdetails.SAMLUserDetailsService;
import org.springframework.stereotype.Service;

/**
 * Implements spring security SAMLUserDetailsService interface:
 * Instantiates PortalUserDetails object from SAML assertion received from
 * keycloak identity provide. Creates Spring Security GrantedAuthority list 
 * based on cancer study roles included in the assertion during authentication
 *
 * @author Kelsey Zhu
 */

@Service
public class SAMLUserDetailsServiceImpl implements SAMLUserDetailsService
{
    private static final Log log = LogFactory.getLog(SAMLUserDetailsServiceImpl.class);

    @Value("${saml.idp.metadata.attribute.email:}")
    private String SAML_IDP_METADATA_EMAIL_ATTR_NAME;

    @Value("${saml.idp.metadata.attribute.role:}")
    private String SAML_IDP_METADATA_ROLE_ATTR_NAME;

    @Value("${app.name:}")
    private String APP_NAME;
    

    /**
     * Defaul no_arg Constructor.
     */
    public SAMLUserDetailsServiceImpl() {
    }

    /**
     * 
     * @param attributeValue
     * @return String type attribute value
     */
    private String getAttributeValue(XMLObject attributeValue)
    {
        return attributeValue == null ?
            null :
            attributeValue instanceof XSString ?
                ((XSString) attributeValue).getValue() :
                attributeValue instanceof XSAnyImpl ?
                    ((XSAnyImpl) attributeValue).getTextContent() :
                    attributeValue.toString();
    }

    /**
     * Implementation of {@code SAMLUserDetailsService}. 
     * Instantiate PortalUserDetails object from  
     * SAML assertion received from identity provider
     */
    @Override
    public Object loadUserBySAML(SAMLCredential credential)
    {
        PortalUserDetails toReturn = null;

        String userId = null;
        List<String> userRoles = new ArrayList<String>();

        // get userid and roles: iterate over attributes searching for "email" and "roles":
        for (Attribute cAttribute : credential.getAttributes()) {
            String attrName = cAttribute.getName();
            log.debug("loadUserBySAML(), parsing attribute -" + attrName);


            if (userId == null && attrName == SAML_IDP_METADATA_EMAIL_ATTR_NAME) {
                userId = credential.getAttributeAsString(cAttribute.getName());
                log.debug(userId);
            }
            if (attrName == SAML_IDP_METADATA_ROLE_ATTR_NAME) {

                List<XMLObject> attributeValues = cAttribute.getAttributeValues();
                if (!attributeValues.isEmpty()) {
                    userRoles.add(new StringBuilder(APP_NAME).append(":").append(
                        getAttributeValue(attributeValues.get(0))).toString());
                }    
            }
        }
        
        try {
            
            if (userId == null) {

                String errorMsg = "loadUserBySAML(), can not instantiate PortalUserDetails from SAML assertion." 
                    + "Expected 'email' attribute was not found or has no values. ";
                log.error(errorMsg);
                throw new Exception(errorMsg);
            }

            log.debug("loadUserBySAML(), IDP successfully authenticated user, userid: " + userId);

            //add granted authorities:
            if (userRoles.size() > 0) toReturn = new PortalUserDetails(userId,
                AuthorityUtils.createAuthorityList(userRoles.toArray(new String[userRoles.size()])));
            else 
                toReturn = new PortalUserDetails(userId, AuthorityUtils.createAuthorityList(new String[0]));
            toReturn.setEmail(userId);
            toReturn.setName(userId);
            return toReturn;
        }
        catch (Exception e) {
            throw new RuntimeException("Error occurs during authentication: " + e.getMessage());
        }
    }
}

