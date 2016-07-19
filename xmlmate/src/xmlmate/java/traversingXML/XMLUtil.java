package traversingXML;
/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 1997-2007 Sun Microsystems, Inc. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common
 * Development and Distribution License("CDDL") (collectively, the
 * "License"). You may not use this file except in compliance with the
 * License. You can obtain a copy of the License at
 * http://www.netbeans.org/cddl-gplv2.html
 * or nbbuild/licenses/CDDL-GPL-2-CP. See the License for the
 * specific language governing permissions and limitations under the
 * License.  When distributing the software, include this License Header
 * Notice in each file and include the License file at
 * nbbuild/licenses/CDDL-GPL-2-CP.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the GPL Version 2 section of the License file that
 * accompanied this code. If applicable, add the following below the
 * License Header, with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 *
 * Contributor(s):
 *
 * The Original Software is NetBeans. The Initial Developer of the Original
 * Software is Sun Microsystems, Inc. Portions Copyright 1997-2006 Sun
 * Microsystems, Inc. All Rights Reserved.
 *
 * If you wish your version of this file to be governed by only the CDDL
 * or only the GPL Version 2, indicate your decision by adding
 * "[Contributor] elects to include this software in this distribution
 * under the [CDDL or GPL Version 2] license." If you do not indicate a
 * single choice of license, a recipient has the option to distribute
 * your version of this file under either the CDDL, the GPL Version 2 or
 * to extend the choice of license to its licensees as provided above.
 * However, if you add GPL Version 2 code and therefore, elected the GPL
 * Version 2 license, then the option applies only if the new code is
 * made subject to such option by the copyright holder.
 */

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.DocumentType;

/**
 * Utility class collecting library methods related to XML processing. Stolen
 * from nbbuild/antsrc and openide/.../xml.
 * 
 * @author Petr Kuzel, Jesse Glick
 */
public final class XMLUtil {

  public static void write(Document doc, File out) throws IOException {
    // XXX note that this may fail to write out namespaces correctly if the
    // document
    // is created with namespaces and no explicit prefixes; however no code in
    // this package is likely to be doing so
    try {
      Transformer t = TransformerFactory.newInstance().newTransformer();
      DocumentType dt = doc.getDoctype();
      if (dt != null) {
        String pub = dt.getPublicId();
        if (pub != null) {
          t.setOutputProperty(OutputKeys.DOCTYPE_PUBLIC, pub);
        }
        t.setOutputProperty(OutputKeys.DOCTYPE_SYSTEM, dt.getSystemId());
      }
      t.setOutputProperty(OutputKeys.ENCODING, "UTF-8"); // NOI18N
      t.setOutputProperty(OutputKeys.INDENT, "yes"); // NOI18N
      t.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4"); // NOI18N
      Source source = new DOMSource(doc);
      Result result = new StreamResult(out);
      t.transform(source, result);
    } catch (Exception e) {
      throw (IOException) new IOException(e.toString()).initCause(e);
    } catch (TransformerFactoryConfigurationError e) {
      throw (IOException) new IOException(e.toString()).initCause(e);
    }
  }

}
