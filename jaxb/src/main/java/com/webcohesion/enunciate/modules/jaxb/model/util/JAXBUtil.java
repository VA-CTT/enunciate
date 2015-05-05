/*
 * Copyright 2006-2008 Web Cohesion
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.webcohesion.enunciate.modules.jaxb.model.util;

import com.webcohesion.enunciate.javac.decorations.DecoratedProcessingEnvironment;
import com.webcohesion.enunciate.javac.decorations.TypeMirrorDecorator;
import com.webcohesion.enunciate.javac.decorations.type.DecoratedDeclaredType;
import com.webcohesion.enunciate.javac.decorations.type.DecoratedTypeMirror;
import com.webcohesion.enunciate.javac.decorations.type.TypeMirrorUtils;
import com.webcohesion.enunciate.modules.jaxb.EnunciateJaxbContext;
import com.webcohesion.enunciate.modules.jaxb.model.Accessor;
import com.webcohesion.enunciate.modules.jaxb.model.adapters.AdapterType;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.type.TypeMirror;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapters;
import java.util.*;

/**
 * Consolidation of common logic for implementing the JAXB contract.
 * 
 * @author Ryan Heaton
 */
@SuppressWarnings ( "unchecked" )
public class JAXBUtil {

  private static final String ADAPTERS_BY_PACKAGE_PROPERTY = "com.webcohesion.enunciate.modules.jaxb.model.util.JAXBUtil#ADAPTERS_BY_PACKAGE";

  private JAXBUtil() {}

  public static DecoratedTypeMirror getComponentType(DecoratedTypeMirror typeMirror, DecoratedProcessingEnvironment env) {
    if (typeMirror.isCollection()) {
      List<? extends TypeMirror> itemTypes = ((DeclaredType) typeMirror).getTypeArguments();
      if (itemTypes.isEmpty()) {
        return TypeMirrorUtils.objectType(env);
      }
      else {
        return (DecoratedTypeMirror) itemTypes.get(0);
      }
    }
    else if (typeMirror instanceof ArrayType) {
      return (DecoratedTypeMirror) ((ArrayType) typeMirror).getComponentType();
    }

    return null;
  }

  public static DecoratedDeclaredType getNormalizedCollection(DecoratedTypeMirror typeMirror, DecoratedProcessingEnvironment env) {
    DecoratedDeclaredType base = typeMirror.isList() ? TypeMirrorUtils.listType(env) : typeMirror.isCollection() ? TypeMirrorUtils.collectionType(env) : null;

    if (base != null) {
      //now narrow the component type to what can be valid xml.
      List<? extends DecoratedTypeMirror> typeArgs = (List<? extends DecoratedTypeMirror>) base.getTypeArguments();
      if (typeArgs.size() == 1) {
        DecoratedTypeMirror componentType = typeArgs.get(0);
        Element element = env.getTypeUtils().asElement(componentType);

        //the interface isn't adapted, check for @XmlTransient and if it's there, narrow it to java.lang.Object.
        //see https://jira.codehaus.org/browse/ENUNCIATE-660
        if (element == null || (element.getAnnotation(XmlJavaTypeAdapter.class) == null && element.getAnnotation(XmlTransient.class) != null)) {
          return base;
        }

        base = (DecoratedDeclaredType) env.getTypeUtils().getDeclaredType((TypeElement) TypeMirrorUtils.collectionType(env).asElement(), componentType);
      }
    }

    return base;
  }

  /**
   * Finds the adapter type for the specified declaration, if any.
   *
   * @param declaration The declaration for which to find that adapter type.
   * @param context The context.
   * @return The adapter type, or null if none was specified.
   */
  public static AdapterType findAdapterType(Element declaration, EnunciateJaxbContext context) {
    DecoratedProcessingEnvironment env = context.getContext().getProcessingEnvironment();
    if (declaration instanceof Accessor) {
      //jaxb accessor can be adapted.
      Accessor accessor = ((Accessor) declaration);
      return findAdapterType(accessor.getAccessorType(), accessor, env.getElementUtils().getPackageOf(accessor.getTypeDefinition()), context);
    }
    else if (declaration instanceof ExecutableElement) {
      //assume the return type of the method is adaptable (e.g. web results, fault bean getters).
      ExecutableElement method = ((ExecutableElement) declaration);
      return findAdapterType((DecoratedTypeMirror) method.getReturnType(), method, env.getElementUtils().getPackageOf(method), context);
    }
    else if (declaration instanceof TypeElement) {
      return findAdapterType((DecoratedDeclaredType) declaration.asType(), null, null, context);
    }
    else {
      throw new IllegalArgumentException("A " + declaration.getClass().getSimpleName() + " is not an adaptable declaration according to the JAXB spec.");
    }
  }

  private static AdapterType findAdapterType(DecoratedTypeMirror unwrappedAdaptedType, Element referer, PackageElement pckg, EnunciateJaxbContext context) {
    DecoratedProcessingEnvironment env = context.getContext().getProcessingEnvironment();
    TypeMirror adaptedType = getComponentType(unwrappedAdaptedType, env);
    XmlJavaTypeAdapter typeAdapterInfo = referer != null ? referer.getAnnotation(XmlJavaTypeAdapter.class) : null;
    if (adaptedType instanceof DeclaredType) {
      if (typeAdapterInfo == null) {
        typeAdapterInfo = ((DeclaredType) adaptedType).asElement().getAnnotation(XmlJavaTypeAdapter.class);
      }

      if ((typeAdapterInfo == null) && (pckg != null)) {
        TypeElement typeDeclaration = (TypeElement) ((DeclaredType) adaptedType).asElement();
        typeAdapterInfo = getAdaptersOfPackage(pckg, context).get(typeDeclaration.getQualifiedName().toString());
      }
    }

    if (typeAdapterInfo != null) {
      DeclaredType adapterTypeMirror;

      try {
        Class adaptedClass = typeAdapterInfo.value();
        adapterTypeMirror = env.getTypeUtils().getDeclaredType(env.getElementUtils().getTypeElement(adaptedClass.getName()));
      }
      catch (MirroredTypeException e) {
        adapterTypeMirror = (DeclaredType) TypeMirrorDecorator.decorate(e.getTypeMirror(), env);
      }

      AdapterType adapterType = new AdapterType(adapterTypeMirror, context.getContext());
      if ((adaptedType instanceof DeclaredType && adapterType.canAdapt(adaptedType, context.getContext())) ||
        (unwrappedAdaptedType != adaptedType && adapterType.canAdapt(unwrappedAdaptedType, context.getContext()))) {
        return adapterType;
      }

      throw new IllegalStateException(referer + ": adapter " + adapterTypeMirror + " does not adapt " + unwrappedAdaptedType);
    }

    return null;

  }

  /**
   * Gets the adapters of the specified package.
   *
   * @param pckg the package for which to get the adapters.
   * @param context The context.
   * @return The adapters for the package.
   */
  private static Map<String, XmlJavaTypeAdapter> getAdaptersOfPackage(PackageElement pckg, EnunciateJaxbContext context) {
    Map<String, Map<String, XmlJavaTypeAdapter>> adaptersOfAllPackages = (Map<String, Map<String,XmlJavaTypeAdapter>>) context.getContext().getProperty(ADAPTERS_BY_PACKAGE_PROPERTY);
    Map<String, XmlJavaTypeAdapter> adaptersOfPackage = adaptersOfAllPackages.get(pckg.getQualifiedName().toString());

    if (adaptersOfPackage == null) {
      adaptersOfPackage = new HashMap<String, XmlJavaTypeAdapter>();
      adaptersOfAllPackages.put(pckg.getQualifiedName().toString(), adaptersOfPackage);

      XmlJavaTypeAdapter javaType = pckg.getAnnotation(XmlJavaTypeAdapter.class);
      XmlJavaTypeAdapters javaTypes = pckg.getAnnotation(XmlJavaTypeAdapters.class);

      if ((javaType != null) || (javaTypes != null)) {
        ArrayList<XmlJavaTypeAdapter> allAdaptedTypes = new ArrayList<XmlJavaTypeAdapter>();
        if (javaType != null) {
          allAdaptedTypes.add(javaType);
        }

        if (javaTypes != null) {
          allAdaptedTypes.addAll(Arrays.asList(javaTypes.value()));
        }

        for (XmlJavaTypeAdapter adaptedTypeInfo : allAdaptedTypes) {
          String typeFqn;

          try {
            Class adaptedClass = adaptedTypeInfo.type();
            if (adaptedClass == XmlJavaTypeAdapter.DEFAULT.class) {
              throw new IllegalStateException("Package " + pckg.getQualifiedName() + ": a type must be specified in " + XmlJavaTypeAdapter.class.getName() + " at the package-level.");
            }
            typeFqn = adaptedClass.getName();

          }
          catch (MirroredTypeException e) {
            TypeMirror adaptedType = e.getTypeMirror();
            if (!(adaptedType instanceof DeclaredType)) {
              throw new IllegalStateException("Package " + pckg.getQualifiedName() + ": unadaptable type: " + adaptedType);
            }

            TypeElement typeDeclaration = (TypeElement) ((DeclaredType) adaptedType).asElement();
            if (typeDeclaration == null) {
              throw new IllegalStateException("Element not found: " + adaptedType);
            }

            typeFqn = typeDeclaration.getQualifiedName().toString();
          }

          adaptersOfPackage.put(typeFqn, adaptedTypeInfo);
        }
      }
    }

    return adaptersOfPackage;
  }
}