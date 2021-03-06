/*
 * Copyright 2011 SURFnet bv, The Netherlands
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package nl.surfnet.coin.teams.interceptor;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import nl.surfnet.coin.api.client.domain.Email;
import nl.surfnet.coin.api.client.domain.Person;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Like the LoginInterceptor but gets the user id from the environment instead
 * of Shibboleth.
 */
public class MockLoginInterceptor extends LoginInterceptor {
  private static final Logger LOG = LoggerFactory.getLogger(MockLoginInterceptor.class);
  private static final String MOCK_USER_ATTR = "mockUser"; 
  
  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
    //no login required for landingpage, css and js
    if (request.getRequestURI().contains("landingpage.shtml") ||
        request.getRequestURI().contains(".js") ||
        request.getRequestURI().contains(".css") ||
        request.getRequestURI().contains(".png")) {
      return true;
    }
    
    HttpSession session = request.getSession();
    if (null == session.getAttribute(PERSON_SESSION_KEY) &&
        StringUtils.isBlank(request.getParameter(MOCK_USER_ATTR))) {
      sendLoginHtml(response);
      return false;
    } else if (null == session.getAttribute(PERSON_SESSION_KEY)) {
      //handle mock user
      String userId = request.getParameter(MOCK_USER_ATTR);
      Person person = new Person();
      person.setId(userId);
      person.setDisplayName(userId);
      Email email = new Email(userId+"@mockorg.org");
      person.addEmail(email);
      session.setAttribute(PERSON_SESSION_KEY, person);
      
      //handle guest status
      session.setAttribute(USER_STATUS_SESSION_KEY,
          getTeamEnvironment().getMockUserStatus());
    }
    return true;
  }
  
  private void sendLoginHtml(HttpServletResponse response) {
    
    try {
      OutputStream out = response.getOutputStream();
      InputStream in = getClass().getClassLoader().getResourceAsStream("mockLogin.html");
      int read = 0;
      byte[] buffer = new byte[1024];
      while (in.available() > 0 && read >= 0) {
        read = in.read(buffer);
        out.write(buffer, 0, read);
      }
    } catch (IOException e) {
      LOG.error("unable to serve the mocklogin html file!", e);
    }
  }
}
