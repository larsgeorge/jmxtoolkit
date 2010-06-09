/**
 * Copyright 2007 The Apache Software Foundation
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.hbase.jmxtoolkit;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.text.DecimalFormat;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.management.InstanceNotFoundException;
import javax.management.IntrospectionException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
import javax.management.MBeanOperationInfo;
import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

/**
 * Allows the creation of JMX attribute lists for given objects as well as
 * the actual query of the attribute values.
 *
 * @author Lars George
 */
public class JMXToolkit {

  private static enum ReturnTypes { NONE, CHAR, STRING, BYTE, SHORT, INTEGER,
    LONG, DOUBLE, FLOAT, BOOLEAN, VOID }
  private static final Pattern VARS = Pattern.compile("\\$\\{\\S+\\}");
  private static final DecimalFormat THRESH = new DecimalFormat("#.##########");
  private static enum CompareResults { LOWER, LOWER_OR_EQUAL, EQUAL, NOT_EQUAL,
    GREATER_OR_EQUAL, GREATER, OK }

  private JMXServiceURL jmxUrl = null;
  private JMXConnector connector = null;
  private MBeanServerConnection connection = null;
  private Map<String, String> params = new HashMap<String, String>();
  private List<Section> sections = new LinkedList<Section>();
  private boolean verbose = false;
  private boolean debug = false;
  private String lineSeparator = System.getProperty("line.separator");

  class CheckDetails {

    private Integer okCode = null;
    private Integer warnCode = null;
    private Integer errorCode = null;
    private String warnThreshold = null;
    private String errorThreshold = null;
    private String warnComparator = ">";
    private String errorComparator = ">";
    private String okMessage = null;
    private String warnMessage = null;
    private String errorMessage = null;

    public CheckDetails(String data) {
      if (data != null) try {
        String[] parts = data.split("\\|");
        if (parts[0].length() > 0) {
          String[] op = parts[0].split(":");
          if (op[0].length() > 0) okCode = new Integer(op[0]);
          if (op.length > 1 && op[1].length() > 0)
            okMessage = op[1];
        }
        if (parts.length > 1 && parts[1].length() > 0) {
          String[] wp = parts[1].split(":");
          if (wp[0].length() > 0) warnCode = new Integer(wp[0]);
          if (wp.length > 1 && wp[1].length() > 0)
            warnMessage = wp[1];
          if (wp.length > 2 && wp[2].length() > 0)
            warnThreshold = wp[2];
          if (wp.length > 3 && wp[3].length() > 0)
            warnComparator = wp[3];
        }
        if (parts.length > 2 && parts[2].length() > 0) {
          String[] ep = parts[2].split(":");
          if (ep[0].length() > 0) errorCode = new Integer(ep[0]);
          if (ep.length > 1 && ep[1].length() > 0)
            errorMessage = ep[1];
          if (ep.length > 2 && ep[2].length() > 0)
            errorThreshold = ep[2];
          if (ep.length > 3 && ep[3].length() > 0)
            errorComparator = ep[3];
        }
      } catch (Exception e) {
        System.err.println("WARNING: Could not parse check details -> " + data);
      }
    }

    public Integer getOkCode() {
      return okCode;
    }

    public void setOkCode(Integer okCode) {
      this.okCode = okCode;
    }

    public Integer getWarnCode() {
      return warnCode;
    }

    public void setWarnCode(Integer warnCode) {
      this.warnCode = warnCode;
    }

    public Integer getErrorCode() {
      return errorCode;
    }

    public void setErrorCode(Integer errorCode) {
      this.errorCode = errorCode;
    }

    public String getWarnThreshold() {
      return warnThreshold;
    }

    public void setWarnThreshold(String warnThreshold) {
      this.warnThreshold = warnThreshold;
    }

    public String getErrorThreshold() {
      return errorThreshold;
    }

    public void setErrorThreshold(String errorThreshold) {
      this.errorThreshold = errorThreshold;
    }

    public String getWarnComparator() {
      return warnComparator;
    }

    public void setWarnComparator(String warnComparator) {
      this.warnComparator = warnComparator;
    }

    public String getErrorComparator() {
      return errorComparator;
    }

    public void setErrorComparator(String errorComparator) {
      this.errorComparator = errorComparator;
    }

    public String getOkMessage() {
      return okMessage;
    }

    public void setOkMessage(String okMessage) {
      this.okMessage = okMessage;
    }

    public String getWarnMessage() {
      return warnMessage;
    }

    public void setWarnMessage(String warnMessage) {
      this.warnMessage = warnMessage;
    }

    public String getErrorMessage() {
      return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
      this.errorMessage = errorMessage;
    }

    public boolean hasErrorCheck() {
      return errorThreshold != null & errorCode != null;
    }

    public boolean hasWarnCheck() {
      return warnThreshold != null & warnCode != null;
    }

    public CompareResults checkForError(String val) {
      return compareValues(val, errorThreshold, errorComparator);
    }

    public CompareResults checkForWarn(String val) {
      return compareValues(val, warnThreshold, warnComparator);
    }

    private CompareResults compareValues(String val, String thresh,
            String comp) {
      int c = 0;
      // try number comparison first, string as a fallback
      try {
        Double v = new Double(val);
        Double t = new Double(thresh);
        c = v.compareTo(t);
      } catch (Exception e) {
        c = val.compareTo(thresh);
      }
      if ("<".equals(comp)) if (c < 0)
        return CompareResults.LOWER; else return CompareResults.OK;
      if ("<=".equals(comp)) if (c <= 0)
        return CompareResults.LOWER_OR_EQUAL; else return CompareResults.OK;
      if ("==".equals(comp)) if (c == 0)
        return CompareResults.EQUAL; else return CompareResults.OK;
      if ("=".equals(comp)) if (c == 0)
        return CompareResults.EQUAL; else return CompareResults.OK;
      if ("!=".equals(comp)) if (c != 0)
        return CompareResults.NOT_EQUAL; else return CompareResults.OK;
      if (">=".equals(comp)) if (c >= 0)
        return CompareResults.GREATER_OR_EQUAL; else return CompareResults.OK;
      if (">".equals(comp)) if (c > 0)
        return CompareResults.GREATER; else return CompareResults.OK;
      throw new IllegalArgumentException("Unknown comparator -> " + comp);
    }

    private String format(String value) {
      try {
        return THRESH.format(value);
      } catch (Exception e) {
        return value;
      }
    }

    @Override
    public String toString() {
      String res = "";
      if (okCode != null || warnCode != null || errorCode != null ||
          warnThreshold != null || errorThreshold != null)
        res = "|" + (okCode != null ? okCode : "") +
          (okMessage != null ? ":" + okMessage : "") +
          "|" + (warnCode != null ? warnCode : "") + ":" +
          (warnMessage != null ? warnMessage : "") + ":" +
          (warnThreshold != null ? format(warnThreshold) : "") + ":" +
          (warnComparator != null ? warnComparator : "") +
          "|" + (errorCode != null ? errorCode : "") + ":" +
          (errorMessage != null ? errorMessage : "") + ":" +
          (errorThreshold != null ? format(errorThreshold) : "") + ":" +
          (errorComparator != null ? errorComparator : "");
      return res;
    }
  }

  /**
   * Base class for attributes and operations.
   */
  class MemberDetails {
    protected String name = null;
    protected ReturnTypes returnType = ReturnTypes.NONE;
    protected CheckDetails checkDetails = null;
    protected Object value = null;

    public MemberDetails(String name, ReturnTypes returnType) {
      this.name = name;
      this.returnType = returnType;
    }

    public MemberDetails(String name, String data) {
      this.name = name;
      if (data != null) {
        String[] parts = data.split("\\|", 2);
        if (parts[0].length() > 0)
          this.returnType = ReturnTypes.valueOf(parts[0].toUpperCase());
        if (parts.length > 1) checkDetails = new CheckDetails(parts[1]);
      }
    }

    public String getName() {
      return name;
    }

    public ReturnTypes getReturnType() {
      return returnType;
    }

    public CheckDetails getCheckDetails() {
      return checkDetails;
    }

    public void setCheckDetails(CheckDetails checkDetails) {
      this.checkDetails = checkDetails;
    }

    public Object getValue() {
      return value;
    }

    public void setValue(Object value) {
      this.value = value;
    }

    public boolean printValue(PrintWriter writer) {
      if (value != null) {
        writer.print(name + ":" + value);
        return true;
      }
      return false;
    }

    @Override
    public int hashCode() {
      return name.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
      return obj == this ||
        (obj instanceof MemberDetails && ((MemberDetails) obj).name.equals(name));
    }

    @Override
    public String toString() {
      String res = returnType != null && returnType != ReturnTypes.NONE ?
        name + "=" + returnType : name;
      if (checkDetails != null) res += checkDetails;
      return res;
    }
  }

  /**
   * Small container class for convenience.
   */
  class AttributeDetails extends MemberDetails {
    public AttributeDetails(String name, ReturnTypes returnType) {
      super(name, returnType);
    }

    public AttributeDetails(String name, String data) {
      super(name, data);
    }
  }

  /**
   * Small container class for convenience.
   */
  class OperationDetails extends MemberDetails {
    public OperationDetails(String name, ReturnTypes returnType) {
      super(name, returnType);
    }

    public OperationDetails(String name, String data) {
      super(name, data);
    }

    @Override
    public String toString() {
      return "*" + super.toString();
    }
  }

  /**
   * Container class for convenience. Holds everything for one "section", which
   * is an object with the attributes, operations and access details.
   */
  class Section {
    private String name = null;
    private String object = null;
    private String regexp = null;
    private String URL = null;
    private String user = null;
    private String password = null;
    private String extendsName = null;
    private Pattern pattern = null;
    private Set<MemberDetails> members = new LinkedHashSet<MemberDetails>();
    private ObjectName objectName = null;
    private boolean connected = false;

    public Section(String name) {
      String n = name != null ? name.trim().replaceAll("^\\[|\\]$", "") : null;
      this.name = n;
    }

    public void add(MemberDetails details) {
      members.add(details);
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public String getObject() {
      return object;
    }

    public void setObject(String object) {
      this.object = object;
    }

    public String getURL() {
      return URL;
    }

    public void setURL(String uRL) {
      URL = uRL;
    }

    public String getUser() {
      return user;
    }

    public void setUser(String user) {
      this.user = user;
    }

    public String getPassword() {
      return password;
    }

    public void setPassword(String password) {
      this.password = password;
    }

    public String getRegexp() {
      return regexp;
    }

    public void setRegexp(String regexp) {
      this.regexp = regexp;
      if (regexp != null) pattern = Pattern.compile(regexp);
    }

    public String getExtendsName() {
      return extendsName;
    }

    public void setExtendsName(String extendsName) {
      this.extendsName = extendsName;
    }

    public Set<MemberDetails> getMembers() {
      return members;
    }

    public void setMembers(Set<MemberDetails> members) {
      this.members = members;
    }

    public ObjectName getObjectName()
    throws MalformedObjectNameException, NullPointerException {
      if (objectName == null && object != null)
        objectName = new ObjectName(object);
      return objectName;
    }

    public void setObjectName(ObjectName objectName) {
      this.objectName = objectName;
    }

    public boolean isConnected() {
      return connected;
    }

    public void setConnected(boolean connected) {
      this.connected = connected;
    }

    public boolean matches(String text) {
      if (pattern == null)
        return object.equals(text);
      else
        return pattern.matcher(text).matches();
    }

    public void printValues(PrintWriter writer) {
      for (MemberDetails detail : members)
        if (detail.printValue(writer)) writer.print(" ");
    }

    public MemberDetails getMember(String attr) {
      String name = attr.startsWith("*") ? attr.substring(1) : attr;
      for (MemberDetails details : members)
        if (details.getName().equals(name)) return details;
      return null;
    }

    @Override
    public String toString() {
      String res = "[" + name + "]" + lineSeparator;
      if (object != null) res += "@object=" + object + lineSeparator;
      if (regexp != null) res += "@regexp=" + regexp + lineSeparator;
      if (URL != null) res += "@url=" + URL + lineSeparator;
      if (extendsName != null) res += "@extends=" + extendsName + lineSeparator;
      if (user != null) res += "@user=" + user + lineSeparator;
      if (password != null) res += "@password=" + password + lineSeparator;
      for (MemberDetails detail : members)
        res += detail + lineSeparator;
      return res;
    }
  }

  /**
   * Constructs a new instance of this class and executes the action.
   *
   * @param args  The command line arguments.
   * @throws InstanceNotFoundException When instantiating the JMX bean fails.
   * @throws IntrospectionException When instantiating the JMX bean fails.
   * @throws ReflectionException When instantiating the JMX bean fails.
   * @throws IOException When talking to the remote JMX server failed.
   */
  public JMXToolkit(String[] args)
  throws InstanceNotFoundException, IntrospectionException, ReflectionException,
  IOException {
    int exitCode = 0;
    parseArgs(args);
    String action = params.get("-a");
    if (action == null) action = params.get("-w") != null ? "check" : "query";
    if (verbose) System.out.println("Action -> " + action);
    if (action.equals("walk")) walk();
    else {
      if (verbose) System.out.println("Reading properties...");
      readProperties();
      if (action.equals("create")) {
        createConfig();
        writeProperties();
      } else if (action.equals("check")) {
        exitCode = checkValue();
      } else if (action.equals("query")) {
        queryValues();
        outputResults();
      } else if (action.equals("encode")) {
        System.out.println(URLEncoder.encode(params.get("-m"), "UTF8"));
      } else {
        System.err.println("Unknown action -> " + action);
        exitCode = -99;
      }
    }
    if (verbose) System.out.println("Exit code -> " + exitCode);
    if (verbose) System.out.println("Done.");
    System.exit(exitCode);
  }

  /**
   * Checks if a value is within certain boundaries.
   * @return The error code.
   * @throws IOException When getting the value fails.
   */
  private int checkValue() throws IOException {
    if (verbose) System.out.println("Checking value...");
    queryValues();
    CheckDetails check = null;
    if (params.get("-w") != null)
      check = new CheckDetails(params.get("-w"));
    Section section = getSection(params.get("-o"));
    String attr = params.get("-q");
    MemberDetails details = section.getMember(attr);
    if (check == null && details.getCheckDetails() != null)
      check = details.getCheckDetails();
    if (check != null) {
      return performCheck(check, details);
    } else {
      throw new IOException("No check defined.");
    }
  }

  /**
   * Does the actual check of a value.
   *
   * @param check  The check to perform.
   * @param details  The details with the value.
   * @return The error code.
   */
  private int performCheck(CheckDetails check, MemberDetails details) {
    String val = details.getValue().toString();
    if (verbose) System.out.println("Details -> " + details + ", value=" + val);
    if (verbose) System.out.println("Check -> " + check);
    if (check.hasErrorCheck()) {
      CompareResults cr = check.checkForError(val);
      if (cr != CompareResults.OK) {
        if (check.getErrorMessage() != null)
          printCheckMessage(check.getErrorMessage(), val);
        return check.getErrorCode();
      }
    }
    if (check.hasWarnCheck()) {
      CompareResults cr = check.checkForWarn(val);
      if (cr != CompareResults.OK) {
        if (check.getWarnMessage() != null)
          printCheckMessage(check.getWarnMessage(), val);
        return check.getWarnCode();
      }
    }
    if (check.getOkMessage() != null)
      printCheckMessage(check.getOkMessage(), val);
    return check.getOkCode() != null ? check.getOkCode() : 0;
  }

  /**
   * Prints a messages using a MessageFormat instance.
   *
   * @param message  The message with place-holders.
   * @param val  The value to fill in.
   */
  private void printCheckMessage(String message, String val) {
    String m = null;
    try {
      m = URLDecoder.decode(message, "UTF8");
    } catch (UnsupportedEncodingException e) {
      // this should never happen
      System.err.println("An error occurred." + e);
    }
    MessageFormat mf = new MessageFormat(m);
    Object[] data = { val };
    System.out.println(mf.format(data));
  }

  /**
   * Returns a named section or <code>null</code>.
   *
   * @param name  The name of the section to retrieve.
   * @return The section or <code>null</code>.
   */
  private Section getSection(String name) {
    for (Section section : sections)
      if (section.getName().equals(name) || section.matches(name))
        return section;
    return null;
  }

  /**
   * Reads the properties file.
   *
   * @throws IOException When the config file is corrupt.
   */
  private void readProperties() throws IOException {
    BufferedReader in = getPropertiesReader();
    if (in != null) {
      Section section = null;
      String line = in.readLine();
      while (line != null) {
        String tl = line.trim();
        // check for empty lines and comments
        if (!tl.startsWith(";") && !tl.startsWith("#") && tl.length() > 0) {
          // check for start of a section
          if (tl.startsWith("[")) {
            section = new Section(tl);
            sections.add(section);
          } else if (section != null) {
            parseLine(section, tl);
          }
        }
        line = in.readLine();
      }
    }
    if (debug) System.out.println("config -> " + sections);
  }

  /**
   * Tries to find the properties on the local file system first and then
   * using the classloader.
   *
   * @return The reader instance or <code>null</code> if not found.
   * @throws FileNotFoundException When the properties file cannot be found.
   */
  private BufferedReader getPropertiesReader() throws FileNotFoundException {
    InputStream in = null;
    String fn = params.get("-f");
    if (fn != null) {
      File f = new File(fn);
      if (f.exists()) {
        in = new FileInputStream(f);
      } else {
        in = JMXToolkit.class.getClassLoader().getResourceAsStream(fn);
      }
      if (in == null)
        System.err.println("WARNING: Could not find configuration file -> " + fn);
    }
    return in != null ? new BufferedReader(new InputStreamReader(in)) : null;
  }

  /**
   * Parses a configuration line. Adds the details into the given section.
   *
   * @param section  The parent section.
   * @param line  The line to parse.
   */
  private void parseLine(Section section, String line) {
    String[] atp = line.split("=", 2);
    String name = atp[0];
    // check if we have a special instruction line
    if (atp.length > 1 && name.startsWith("@")) {
      String val1 = replaceVariables(atp[1], true);
      String val2 = replaceVariables(atp[1], false);
      if (name.equalsIgnoreCase("@object")) section.setObject(val2);
      if (name.equalsIgnoreCase("@regexp")) section.setRegexp(val2);
      if (name.equalsIgnoreCase("@url")) section.setURL(val1);
      if (name.equalsIgnoreCase("@extends")) section.setExtendsName(val1);
      if (name.equalsIgnoreCase("@user")) section.setUser(val1);
      if (name.equalsIgnoreCase("@password")) section.setPassword(val1);
      return;
    }
    // otherwise assume an attribute or an operation
    String data = atp.length > 1 ? atp[1] : null;
    // operations have a leading "*" - which would be illegal otherwise
    MemberDetails details = !line.startsWith("*") ?
      new AttributeDetails(name, data) :
      new OperationDetails(name.substring(1), data);
    section.add(details);
  }

  /**
   * Replaces a system property with a variable.
   *
   * @param value  The value to parse and replace within.
   * @param keepVars  Flag to keep the variable in place.
   * @return The value with the replaced variables.
   */
  private String replaceVariables(String value, boolean keepVars) {
    Matcher m = VARS.matcher(value);
    StringBuilder res = new StringBuilder();
    int start = 0;
    while (m.find()) {
      res.append(value.substring(start, m.start()));
      // get variable but remove ${...} in the process
      String v = value.substring(m.start() + 2, m.end() - 1);
      String[] vp = v.split("\\|");
      String defVal = v.length() > 1 ? vp[1] : "";
      String p = System.getProperty(vp[0], defVal);
      // replace fully when there is no default
      if (vp.length == 1 || !keepVars) res.append(p);
      else {
        //  otherwise keep place holder but replace default
        String d = p.length() != 0 ? p : vp[1];
        res.append("${").append(vp[0]).append("|").append(d).append("}");
      }
      start = m.end();
    }
    res.append(value.substring(start));
    return res.toString();
  }

  /**
   * Processes the request.
   *
   * @throws IOException When connecting to the host or retrieval fails.
   */
  private void createConfig()
  throws IOException {
    if (verbose) System.out.println("Creating configuration...");
    for (Section section : sections) retrieveMembers(section, true);
  }

  /**
   * Retrieves all current attributes and operations for a given object.
   *
   * @param section  The section defining the object.
   * @param close <code>true</code> when the connection should be closed.
   * @throws IOException When connecting to the host or retrieval fails.
   */
  private void retrieveMembers(Section section, boolean close)
  throws IOException {
    openConnection(section);
    try {
      // iterate over objects
      Set<ObjectName> names = connection.queryNames(null, null);
      for (ObjectName on : names) {
        if (verbose) System.out.println("checking object -> " + on.getCanonicalName());
        if (section.matches(on.getCanonicalName())) {
          if (verbose) System.out.println("match found -> " + on.getCanonicalName());
          MBeanInfo info;
          try {
            info = connection.getMBeanInfo(on);
          } catch (Exception e) {
            throw new IOException(e);
          }
          addAttributes(section, info);
          addOperations(section, info);
        }
      }
    } finally {
      closeConnection(section);
    }
  }

  /**
   * Writes the properties file out.
   *
   * @throws FileNotFoundException When writing the properties fails.
   */
  private void writeProperties() throws FileNotFoundException {
    if (verbose) System.out.println("Writing configuration...");
    OutputStream out = System.out;
    if (params.containsKey("-f") && !params.containsKey("-x"))
      out = new FileOutputStream(new File(params.get("-f")));
    PrintWriter pw = new PrintWriter(out);
    for (Section section : sections)
      pw.println(section);
    pw.close();
  }

  /**
   * Queries the values for specific attributes and operations.
   *
   * @throws IOException When getting the values fails.
   */
  private void queryValues() throws IOException {
    if (verbose) System.out.println("Querying values...");
    List<Section> querySections = new LinkedList<Section>();
    if (params.get("-o") != null) {
      Section section = getSection(params.get("-o"));
      if (section != null) querySections.add(section);
      else throw new IOException("No matching section found");
    } else {
      querySections.addAll(sections);
    }
    String attr = params.get("-q");
    // iterate over all selected sections
    for (Section section : querySections) {
      if (section.getMembers().size() == 0)
        retrieveMembers(section, false);
      if (!section.isConnected()) openConnection(section);
      try {
        findObjectName(section);
        if (verbose) System.out.println("Querying object -> " + section.getObject());
        if (attr != null) {
          MemberDetails details = section.getMember(attr);
          getMemberValue(section, details);
        } else {
          for (MemberDetails details : section.getMembers())
            getMemberValue(section, details);
        }
      } finally {
        closeConnection(section);
      }
    }
  }

  /**
   * Gets the actual value based on the type of the member, i.e. attribute
   * or operation.
   *
   * @param section  The section with the object name.
   * @param details  The member to query.
   * @return The result as on {@link Object} or <code>null</code>.
   * @throws IOException When anything fails during the call.
   */
  private Object getMemberValue(Section section, MemberDetails details)
  throws IOException {
    Object res = null;
    try {
      if (details instanceof AttributeDetails) {
        res = connection.getAttribute(section.getObjectName(), details.getName());
      } else if (details instanceof OperationDetails) {
        res = connection.invoke(section.getObjectName(), details.getName(),
          new Object[]{}, new String[]{});
      }
    } catch (IOException e) {
      throw e;
    } catch (Exception e) {
      if (!params.containsKey("-l"))
        throw new IOException(e);
    }
    if (res != null) details.setValue(res);
    return res;
  }

  /**
   * Finds the matching ObjectName when a section has a true pattern.
   *
   * @param section  The section to find the object name for.
   * @throws IOException When querying the names fails.
   */
  private void findObjectName(Section section)
  throws IOException {
    String regexp = params.get("-e");
    if (regexp == null) regexp = section.getRegexp();
    if (regexp != null) {
      // iterate over objects
      Set<ObjectName> names = connection.queryNames(null, null);
      for (ObjectName on : names) {
        if (verbose) System.out.println("checking object -> " + on.getCanonicalName());
        if (section.matches(on.getCanonicalName())) {
          if (verbose) System.out.println("match found -> " + on.getCanonicalName());
          section.setObjectName(on);
          return;
        }
      }
      throw new IOException("Could not find matching ObjectName -> " + regexp);
    }
  }

  /**
   * Outputs the query results.
   */
  private void outputResults() {
    if (verbose) System.out.println("Printing results..." + lineSeparator);
    PrintWriter writer = new PrintWriter(System.out);
    for (Section section : sections) section.printValues(writer);
    writer.println();
    writer.close();
  }

  /**
   * Opens the connection to the JMX host.
   *
   * @param section  The optional section with a specific URL.
   * @throws IOException When the connection fails.
   */
  private void openConnection(Section section) throws IOException {
    // get global connection and then the specific one if given
    String url = params.get("-u");
    if (section != null && section.getURL() != null)
      url = section.getURL();
    // create connection URL
    jmxUrl = new JMXServiceURL(replaceVariables(url, false));
    // add credentials if given
    Map<String, String[]> m = new HashMap<String, String[]>();
    String user = params.get("-c");
    if (section != null && section.getUser() != null)
      user = section.getUser();
    String pass = params.get("-p");
    if (section != null && section.getPassword() != null)
      pass = section.getPassword();
    if (user != null)
      m.put(JMXConnector.CREDENTIALS, new String[]{ replaceVariables(user, false),
        pass != null ? replaceVariables(pass, false) : null });
    // create JMX connection
    connector = JMXConnectorFactory.connect(jmxUrl, m);
    connection = connector.getMBeanServerConnection();
    if (section != null) section.setConnected(true);
  }

  /**
   * Closes the connection.
   *
   * @param section  The optional section with a specific URL.
   * @throws IOException When closing the connection fails.
   */
  private void closeConnection(Section section) throws IOException {
    if (connector != null) connector.close();
    if (section != null) section.setConnected(false);
  }

  /**
   * Extracts the details about the available attributes.
   *
   * @param section  The section to add the details to.
   * @param info  The MBean info to query.
   */
  private void addAttributes(Section section, MBeanInfo info) {
    for (MBeanAttributeInfo mbi : info.getAttributes()) {
      String name = mbi.getName();
      String atr = mbi.getType();
      String at = atr;
      if (atr != null) {
        String[] atp = atr.split("\\.");
        at = atp[atp.length - 1];
        if (at.equals("int")) at = "Integer";
      }
      if (verbose) System.out.println("attribute name -> " + name +
        ", type -> " + at + ", raw type -> " + atr);
      ReturnTypes returnType = ReturnTypes.NONE;
      try {
        returnType = ReturnTypes.valueOf(at.toUpperCase());
      } catch (Exception e) {
        System.err.println("WARNING: Unsupported attribute return type -> " +
          at + ", attribute -> " + name);
      }
      AttributeDetails ad = new AttributeDetails(name, returnType);
      section.add(ad);
    }
  }

  /**
   * Extracts the details about the available operations.
   *
   * @param section  The section to add the details to.
   * @param info  The MBean info to query.
   */
  private void addOperations(Section section, MBeanInfo info) {
    for (MBeanOperationInfo mbi : info.getOperations()) {
      String name = mbi.getName();
      String atr = mbi.getReturnType();
      String at = atr;
      if (atr != null) {
        String[] atp = atr.split("\\.");
        at = atp[atp.length - 1];
        if (at.equals("int")) at = "Integer";
      }
      if (verbose) System.out.println("attribute name -> " + name +
        ", type -> " + at + ", raw type -> " + atr);
      ReturnTypes returnType = ReturnTypes.NONE;
      try {
        returnType = ReturnTypes.valueOf(at.toUpperCase());
      } catch (Exception e) {
        System.err.println("WARNING: Unsupported operation return type -> " +
          atr + ", operation -> " + name);
      }
      OperationDetails od = new OperationDetails(name, returnType);
      section.add(od);
    }
  }

  /**
   * Walks the remote JMX objects.
   *
   * @throws IOException When opening the JMX connection fails.
   * @throws javax.management.InstanceNotFoundException When querying the JMX MBeans fails.
   * @throws javax.management.IntrospectionException When querying the JMX MBeans fails.
   * @throws javax.management.ReflectionException When querying the JMX MBeans fails.
   */
  private void walk()
  throws IOException, IntrospectionException, InstanceNotFoundException, ReflectionException {
    openConnection(null);
    Set<ObjectName> names = connection.queryNames(null, null);
    for (ObjectName on : names) {
      System.out.println("object -> " + on.getCanonicalName());
      MBeanInfo info = connection.getMBeanInfo(on);
      for (MBeanAttributeInfo mbi : info.getAttributes()) {
        System.out.println("  attribute name -> " + mbi.getName() + ", type -> " + mbi.getClass());
        try {
          Object attr = connection.getAttribute(on, mbi.getName());
          if (attr instanceof CompositeDataSupport) {
            CompositeDataSupport cds2 = (CompositeDataSupport) attr;
            Set<String> keys = cds2.getCompositeType().keySet();
            for (String key : keys) {
              System.out.println("  attribute value -> " + key + " " + cds2.get(key));
            }
          } else {
            if (attr.getClass().isArray()) {
              Object[] a = (Object[]) attr;
              for (int i = 0; i < a.length; i++) {
                System.out.println("  attribute value[" + i + "] -> " + a[i]);
              }
            } else {
              System.out.println("  attribute value -> " + attr.toString());
            }
          }
        } catch (Exception e) {
          System.err.println("Error reading attribute -> " + mbi.getName());
          if (verbose) e.printStackTrace(System.err);
        }
      }
    }
    closeConnection(null);
  }

  /**
   * Prints the usage of the class.
   */
  private void printUsage() {
    System.out.println("Usage: JMXToolkit [-a <action>] [-c <user>]" +
      " [-p <password>] [-u url] [-f <config>] [-o <object>]\n" +
      " [-e regexp] [-i <extends>] [-q <attr-oper>] [-w <check>]" +
      " [-m <message>] [-x] [-l] [-v] [-h]\n\n" +
      "\t-a <action>\tAction to perform, can be one of the following (default: query)\n\n" +
      "\t\t\tcreate\tScan a JMX object for available attributes\n" +
      "\t\t\tquery\tQuery a set of attributes from the given objects\n" +
      "\t\t\tcheck\tChecks a given value to be in a valid range (see -w below)\n" +
      "\t\t\tencode\tHelps creating the encoded messages (see -m and -w below)\n" +
      "\t\t\twalk\tWalk the entire remote object list\n\n" +
      "\t-c <user>\tThe user role to authenticate with (default: controlRole)\n" +
      "\t-p <password>\tThe password to authenticate with (default: password)\n" +
      "\t-u <url>\tThe JMX URL (default: service:jmx:rmi:///jndi/rmi://localhost:10001/jmxrmi)\n" +
      "\t-f <config>\tThe config file to use (default: none)\n" +
      "\t-o <object>\tThe JMX object query (default: none)\n" +
      "\t-e <regexp>\tThe regular expression to match (default: none)\n" +
      "\t-i <extends>\tThe name of the object that is inherited from (default: none)\n" +
      "\t-q <attr-oper>\tThe attribute or operation to query (default: none)\n" +
      "\t-w <check>\tUsed with -a check to define thresholds (default: none)\n\n" +
      "\t\tFormat: <ok-exitcode>[:<ok-message>] \\\n" +
      "\t\t        |<warn-exitcode>:[<warn-message>]:<warn-value>[:<warn-comparator>] \\\n" +
      "\t\t        [|<error-exitcode>:[<error-message>]:<error-value>[:<error-comparator>]]\n\n" +
      "\t\tExample for Nagios and DFS used (in %):\n\n" +
      "\t\t        0:OK%3A%20%7B0%7D|2:WARN%3A%20%7B0%7D:80:>=|1:FAIL%3A%20%7B0%7D:95:>\n\n" +
      "\t\tNotes: Messages are URL-encoded to allow for any character being used. The current value\n" +
      "\t\t       can be placed with {0} in the message. Allowed comparators: <,<=,=,==,>=,>\n\n" +
      "\t-m <message>\tThe message to encode for further use (default: none)\n" +
      "\t-x\t\tOutput config to console (do not write back to -f <config>)\n" +
      "\t-l\t\tIgnore missing attributes, do not throw an error\n" +
      "\t-v\t\tVerbose output\n" +
      "\t-h\t\tPrints this help\n"
      );
  }

  /**
   * Parses the command line arguments.
   *
   * @param args  The command line arguments.
   */
  private void parseArgs(String[] args) {
    List<String> ar = args != null ? Arrays.asList(args) : null;
    if (ar == null || ar.size() == 0 || ar.contains("-h")) {
      printUsage();
      System.exit(0);
    }
    // read command line arguments
    for (int n = 0; n < args.length; n++) {
      if (args[n].startsWith("-")) {
        String key = args[n];
        String val = null;
        if (args.length > n+1 && !args[n+1].startsWith("-")) val = args[++n];
        params.put(key, val);
      }
    }
    // switches
    debug = params.containsKey("-d");
    verbose = params.containsKey("-v") || debug;
    // add details if given and no config used
    String object = params.get("-o");
    if (object != null && params.get("-f") == null) {
      Section section = new Section(object);
      if (params.get("-e") == null)
        section.setObject(object);
      else
        section.setRegexp(params.get("-e"));
      section.setUser(params.get("-c"));
      section.setPassword(params.get("-p"));
      section.setExtendsName(params.get("-i"));
      String attrib = params.get("-q");
      if (attrib != null && attrib.length() > 1) {
        MemberDetails details = !attrib.startsWith("*") ?
          new AttributeDetails(attrib, ReturnTypes.NONE) :
          new OperationDetails(attrib.substring(1), ReturnTypes.NONE);
        section.add(details);
      }
      sections.add(section);
    }
    // dump parameters
    if (debug) System.out.println("parameters -> " + params);
  }

  /**
   * Main entry point to this class.
   *
   * @param args  The command line arguments.
   */
  public static void main(String[] args) {
    int error = 0;
    try {
      new JMXToolkit(args);
    } catch (NullPointerException e) {
      System.out.println("Missing parameter (either -u or -f _must_ be given). " + e);
      e.printStackTrace();
      error = 1;
    } catch (MalformedURLException e) {
      System.out.println("Bad JMX URI. " + e);
      error = 2;
    } catch (InstanceNotFoundException e) {
      System.out.println("Instance not found. " + e);
      error = 3;
    } catch (IntrospectionException e) {
      System.out.println("Introspection error. " + e);
      error = 4;
    } catch (ReflectionException e) {
      System.out.println("Reflection error. " + e);
      error = 5;
    } catch (IOException e) {
      System.out.println("IO error. " + e);
      error = 6;
    }
    System.exit(error);
  }
}
