/**
 * Copyright © 2006-2016 Web Cohesion (info@webcohesion.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.webcohesion.enunciate.modules.jaxb.model.types;

import javax.lang.model.type.PrimitiveType;
import javax.xml.namespace.QName;

/**
 * @author Ryan Heaton
 */
public class XmlPrimitiveType implements XmlType {

  private final PrimitiveType type;

  public XmlPrimitiveType(PrimitiveType delegate) {
    this.type = delegate;
  }

  public String getName() {
    switch (this.type.getKind()) {
      case BOOLEAN:
        return "boolean";
      case BYTE:
        return "byte";
      case DOUBLE:
        return "double";
      case FLOAT:
        return "float";
      case INT:
        return "int";
      case LONG:
        return "long";
      case SHORT:
        return "short";
      case CHAR:
        return "unsignedShort";
    }

    return null;
  }

  public String getNamespace() {
    return "http://www.w3.org/2001/XMLSchema";
  }

  public QName getQname() {
    return new QName(getNamespace(), getName());
  }

  public boolean isAnonymous() {
    return false;
  }

  public boolean isSimple() {
    return true;
  }

}
