/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/JSP_Servlet/Servlet.java to edit this template
 */

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import static java.nio.charset.StandardCharsets.US_ASCII;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.Random;
import java.util.logging.Logger;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 *
 * @author ludo
 */
public class newServlet extends HttpServlet {
private static final Charset US_ASCII_CHARSET = US_ASCII;

  private final Random random = new Random();
 Logger logger = Logger.getLogger("aaa");
  @Override
  public void doGet(HttpServletRequest req, HttpServletResponse res)
      throws IOException, ServletException {

    // Request URL is of the form "/testCloudSQL?instance=inst&user=name"
    // - The parameter 'instance' is mandatory.
    // - The parameter 'user' is optional and has a default value of "root"
    //
    // Additional optional parameters:
    // insertRows=int: insert the given number of rows
    // batch=true|false: whether to use batch statements (default: false)
    // transaction=true|false: whether to use a transaction for insertions
    //   (default: false)
    // timeout=int: timeout to use for each statement (default: 0, meaning "no limit")

    String instance = req.getParameter("instance");
    if (instance == null) {
      throw new IllegalArgumentException("Instance name must be specified");
    }
logger.info("");
      logger.info("instance IS " +instance);
    boolean useBatch = Boolean.parseBoolean(req.getParameter("batch"));
    boolean useTransaction = Boolean.parseBoolean(req.getParameter("transaction"));
    boolean rewriteBatchedStatements = Boolean.parseBoolean(
        req.getParameter("rewriteBatchedStatements"));
    int rowsToInsert = getIntParameter("insertRows", req);
    int columns = getIntParameter("columns", 10, req);
    int valueLength = getIntParameter("valueLength", 10, req);
    int timeout = getIntParameter("timeout", req);

    String dburl = "jdbc:google:mysql://" + instance;
//export INSTANCE_CONNECTION_NAME='<MY-PROJECT>:<INSTANCE-REGION>:<INSTANCE-NAME>'
// "?instance=google.com:cloudsql-test-app:appengine-integration-test&user=java8&password=java8";

//https://mysql-dot-ludo-in-in.uc.r.appspot.com/foo?instance=google.com:cloudsql-test-app:us-central1:appengine-integration-test&user=java8&password=java8
//https://mysql-dot-ludo-in-in.uc.r.appspot.com/foo?instance=ludo-in-in:us-central1:mysql-instance&user=java8&password=java8
//https://mysql-dot-ludo-in-in.uc.r.appspot.com/foo?instance=ludo-in-in:us-central1:mysql-instance&user=root
//ludo-in-in:us-central1:mysql-instance
    try {
      Class.forName("com.mysql.jdbc.GoogleDriver");
    } catch (ClassNotFoundException e) {
      throw new ServletException("Error loading Google JDBC Driver", e);
    }

    if (rowsToInsert == 0) {
      testConnectivity(dburl, res);
    } else {
      testRowInsertion(dburl, rowsToInsert, columns, valueLength, useTransaction, useBatch,
          rewriteBatchedStatements, timeout, res);
    }
  }

  private void testConnectivity(String dburl,
          HttpServletResponse res)
      throws IOException, ServletException {
    int rows = 0;
    int cols = 0;
      logger.info("db==="+ dburl);
    try (Connection conn = DriverManager.getConnection(dburl)) {
      ResultSet result = conn.createStatement().executeQuery("SELECT 1");
      ResultSetMetaData metadata = result.getMetaData();
      cols = metadata.getColumnCount();
      while (result.next()) {
        rows++;
      }
    } catch (SQLException e) {
      throw new ServletException("SQL error", e);
    }

    if (rows == 1 && cols == 1) {
      doResponse(res, "PASS");
    } else {
      doResponse(res, "FAIL");
    }
  }

  private void testRowInsertion(String dburl, int rowsToInsert, int columns, int valueLength,
      boolean useTransaction, boolean useBatch, boolean rewriteBatchedStatements, int timeout,
      HttpServletResponse res) throws IOException, ServletException {
    // Insert rowsToInsert rows into a new table. The table name is chosen randomly
    // to avoid conflicts between test runs.
    if (rewriteBatchedStatements) {
      dburl += "&rewriteBatchedStatements=true";
    }
    try (Connection conn = DriverManager.getConnection(dburl)) {
      String tableName = String.format("cloudSqlServletTest_%d", random.nextInt(1000000));
      conn.createStatement().executeUpdate(drop(tableName));
      try {
        conn.createStatement().executeUpdate(create(tableName, columns));
        if (useTransaction) {
          conn.setAutoCommit(false);
        }
        try {
//          PreparedStatement s = conn.prepareStatement(insertInto(tableName, columns));
//          s.setQueryTimeout(timeout);
//          for (int i = 0; i < rowsToInsert; ++i) {
//            for (int j = 1; j <= columns; ++j) {
//              s.setString(j, createRandomString(valueLength));
//            }
//            if (useBatch) {
//              s.addBatch();
//            } else {
//              s.executeUpdate();
//            }
//          }
//          if (useBatch) {
//            s.executeBatch();
//          }
//          if (useTransaction) {
//            conn.commit();
//          }
        } finally {
          if (useTransaction) {
            conn.setAutoCommit(true);
          }
        }
      } finally {
        // Drop the test table.
        conn.createStatement().executeUpdate(drop(tableName));
      }
    } catch (SQLException e) {
      throw new ServletException("Error executing SQL", e);
    }
    doResponse(res, "PASS");
  }

  private String drop(String tableName) {
    return String.format("DROP TABLE IF EXISTS %s", tableName);
  }

  private String create(String tableName, int columns) {
    StringBuilder sb = new StringBuilder();
    sb.append("CREATE TABLE ");
    sb.append(tableName);
    sb.append(" (");
    for (int j = 1; j <= columns; ++j) {
      if (j != 1) {
        sb.append(", ");
      }
      sb.append("s");
      sb.append(j);
      sb.append(" VARCHAR(255)");
    }
    sb.append(")");
    return sb.toString();
  }

//  private String insertInto(String tableName, int columns) {
//    return String.format("INSERT INTO %s VALUES (?" + Strings.repeat(",?", columns - 1) + ")",
//        tableName);
//  }

  private void doResponse(HttpServletResponse res, String s) throws IOException {
    res.setContentType("text/plain");
    res.getWriter().println(s);
  }

  private int getIntParameter(String name, HttpServletRequest req) throws ServletException {
    return getIntParameter(name, 0, req);
  }

  private int getIntParameter(String name, int defaultValue, HttpServletRequest req)
      throws ServletException {
    String value = req.getParameter(name);
    if (value != null) {
      try {
        return Integer.parseInt(value);
      } catch (NumberFormatException e) {
        throw new ServletException("parameter " + name + "is not a valid integer");
      }
    }
    return defaultValue;
  }

  private String createRandomString(int size) {
    byte[] bytes = new byte[size];
    for (int i = 0; i < size; ++i) {
      bytes[i] = (byte) (random.nextInt(127 - 32) + 32);
    }
    return new String(bytes, US_ASCII_CHARSET);
  }
}
