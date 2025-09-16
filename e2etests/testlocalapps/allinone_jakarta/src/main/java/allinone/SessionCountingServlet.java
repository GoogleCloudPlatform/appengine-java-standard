/*
 * Copyright 2021 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package allinone;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * A servlet that uses an HttpSession to track the number of times that it has been invoked,
 * reporting that count in its response.
 */
@WebServlet(name = "SessionCountingServlet", value = "/session")
public class SessionCountingServlet extends HttpServlet {
  private static final long serialVersionUID = 1L;

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    Integer count;

    HttpSession session = request.getSession(true);
    synchronized (session) {
      count = (Integer) session.getAttribute("count" + System.getenv("GAE_DEPLOYMENT_ID"));
      if (count == null) {
        count = 0;
      }
      session.setAttribute("count" + System.getenv("GAE_DEPLOYMENT_ID") , count + 1);
    }

    response.setContentType("text/html;charset=UTF-8");
    PrintWriter writer = response.getWriter();
    writer.println("Count=" + count);
  }
}
