/*******************************************************************************
 * Copyright (c) 2009 SpringSource and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Andrew Eisenberg - initial API and implementation
 *******************************************************************************/

package org.eclipse.ajdt.core.model;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.aspectj.ajde.core.AjCompiler;
import org.aspectj.ajdt.internal.core.builder.AjBuildManager;
import org.aspectj.ajdt.internal.core.builder.AjState;
import org.aspectj.ajdt.internal.core.builder.IncrementalStateManager;
import org.aspectj.asm.IProgramElement;
import org.aspectj.asm.IProgramElement.Accessibility;
import org.aspectj.asm.internal.ProgramElement;
import org.aspectj.weaver.ConcreteTypeMunger;
import org.aspectj.weaver.ResolvedMember;
import org.aspectj.weaver.ResolvedType;
import org.aspectj.weaver.ResolvedTypeMunger;
import org.aspectj.weaver.TypeVariable;
import org.aspectj.weaver.UnresolvedType;
import org.aspectj.weaver.World;
import org.eclipse.ajdt.core.AspectJPlugin;
import org.eclipse.ajdt.core.javaelements.IntertypeElement;
import org.eclipse.core.resources.IProject;
import org.eclipse.jdt.core.ITypeParameter;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.core.compiler.CharOperation;

/**
 * @author Andrew Eisenberg
 * @created Jan 13, 2009
 *
 * This class provides AJDT access to the {@link World} object.
 * Clients must not hold a reference to any world object
 * for longer than necessary
 */
public final class AJWorldFacade {
    public static class ErasedTypeSignature {
        public ErasedTypeSignature(String returnTypeSig, String[] paramTypes, TypeParameter[] typeParameters) {
            this.returnTypeSig = returnTypeSig;
            this.paramTypes = paramTypes;
            this.typeParameters = typeParameters;
        }
        public ErasedTypeSignature(String returnType, String[] paramTypes) {
            this.returnTypeSig = returnType;
            this.paramTypes = paramTypes;
            this.typeParameters = null;
        }
        public final String returnTypeSig;
        public final String[] paramTypes;
        public final TypeParameter[] typeParameters;
    }

    public static class ITDInfo {
        static ITDInfo create(ConcreteTypeMunger cMunger, boolean includeTypeParameters) {
            ResolvedType aspectType = cMunger.getAspectType();
            if (aspectType != null) {
                ResolvedMember sig = cMunger.getSignature();
                Accessibility a = sig != null ?
                        ProgramElement.genAccessibility(sig.getModifiers()) :
                        Accessibility.PUBLIC;
                String packageDeclaredIn = aspectType.getPackageName();
                String topLevelAspectName = aspectType.getOutermostType().getClassName();
                TypeParameter[] convertedTypeParameters;
                if (includeTypeParameters) {
                    convertedTypeParameters = convertTypeParameters(sig.getTypeVariables());
                } else {
                    convertedTypeParameters = null;
                }
                return new ITDInfo(a, packageDeclaredIn, topLevelAspectName, convertedTypeParameters);
            } else {
                return null;
            }
        }

        public ITDInfo(Accessibility accessibility, String packageDeclaredIn,
                String topLevelAspectName, TypeParameter[] typeParameters) {
            this.accessibility = accessibility;
            this.packageDeclaredIn = packageDeclaredIn;
            this.topLevelAspectName = topLevelAspectName;
            this.typeParameters = typeParameters;
        }
        public final Accessibility accessibility;
        public final String packageDeclaredIn;
        public final String topLevelAspectName;
        public final TypeParameter[] typeParameters;

        public ITypeParameter[] getITypeParameters(IntertypeElement parent) {
            if (typeParameters == null) {
                return null;
            }

            ITypeParameter[] itypeParameters = new ITypeParameter[typeParameters.length];
            for (int i = 0; i < typeParameters.length; i++) {
                itypeParameters[i] = new org.eclipse.jdt.internal.core.TypeParameter(parent, typeParameters[i].name);
            }
            return itypeParameters;
        }
    }

    // FIXADE do we even need this class?  maybe just need the parameter name
    public static class TypeParameter {
        public final String name;
        public final String upperBoundTypeName;
        public TypeParameter(String name, String upperBoundTypeName) {
            this.name = name;
            this.upperBoundTypeName = upperBoundTypeName;
        }
    }

    private static final TypeParameter[] NO_TYPE_PARAMETERS = new TypeParameter[0];

    private final AjBuildManager manager;
    private final World world;

    public AJWorldFacade(IProject project) {
        AjCompiler compiler = AspectJPlugin.getDefault().getCompilerFactory().getCompilerForProject(project);
        AjState state = IncrementalStateManager.retrieveStateFor(compiler.getId());
        if (state != null) {
            manager = state.getAjBuildManager();
            world = manager.getWorld();
        } else {
            manager = null;
            world = null;
        }
    }

    /**
     * @param typeVariables
     * @return
     */
    static TypeParameter[] convertTypeParameters(
            TypeVariable[] typeVariables) {
        if (typeVariables == null || typeVariables.length == 0) {
            return NO_TYPE_PARAMETERS;
        }
        TypeParameter[] typeParameters = new TypeParameter[typeVariables.length];

        for (int i = 0; i < typeVariables.length; i++) {
            String name = typeVariables[i].getName();
            UnresolvedType upperBoundMunger = typeVariables[i].getFirstBound();
            String upperBound;
            if (upperBoundMunger != null) {
                upperBound = createJDTSignature(upperBoundMunger.getBaseName());
            } else {
                upperBound = null;
            }
            typeParameters[i] = new TypeParameter(name, upperBound);
        }
        return typeParameters;
    }

    private Map<char[], List<ConcreteTypeMunger>> cachedMungers;

    private void cacheMunger(char[] typeName, List<ConcreteTypeMunger> mungers) {
        if (cachedMungers == null) {
            cachedMungers = new HashMap<>();
        }
        cachedMungers.put(typeName, mungers);
    }



    public ITDInfo findITDInfoFromDeclaringType(char[] declaringTypeSignature, char[] name) {
        if (world == null || declaringTypeSignature == null) {
            return null;
        }
        List<ConcreteTypeMunger> itds;
        String nameStr = new String(name);
        ResolvedType type;
        try {
            String sig = createAJSignature(declaringTypeSignature);
            type = world.getCoreType(UnresolvedType.forSignature(sig));
        } catch (Exception e) {
            // can't do much here
            return null;
        }
        if (type == null || type.isMissing() || type.crosscuttingMembers == null) {
            return null;
        }
        itds = type.crosscuttingMembers.getTypeMungers();

        if (itds == null) {
            return null;
        }

        for (ConcreteTypeMunger concreteTypeMunger : itds) {
            ResolvedTypeMunger munger = concreteTypeMunger.getMunger();
            if (munger == null) {
                continue;
            }
            if (munger.getKind() == ResolvedTypeMunger.Field) {
                if (munger.getSignature().getName().equals(nameStr)) {
                    return ITDInfo.create(concreteTypeMunger, true);
                }
            }

            if (munger.getKind() == ResolvedTypeMunger.Method) {
                // also need to compare parameters, but parameters
                // are expensive to calculate
                if (nameStr.endsWith("." + munger.getSignature().getName())) {
                    return ITDInfo.create(concreteTypeMunger, true);
                }
            }
        }
        return null;
    }

    public ITDInfo findITDInfoFromTargetType(char[] targetTypeSignature, char[] name) {
        if (world == null || targetTypeSignature == null) {
            return null;
        }
        List<ConcreteTypeMunger> itds;
        String nameStr = new String(name);
        if (cachedMungers != null && cachedMungers.containsKey(targetTypeSignature)) {
            itds = cachedMungers.get(targetTypeSignature);
            if (itds == null) {
                return null;
            }
        } else {
            ResolvedType type;
            try {
                String sig = createAJSignature(targetTypeSignature);
                type = world.getCoreType(UnresolvedType.forSignature(sig));
            } catch (Exception e) {
                // don't cache
                return null;
            }
            if (type == null || type.isMissing()) {
                cacheMunger(targetTypeSignature, null);
                return null;
            }
            itds = type.getInterTypeMungersIncludingSupers();
            cacheMunger(targetTypeSignature, itds);
        }

        for (ConcreteTypeMunger concreteTypeMunger : itds) {
            ResolvedTypeMunger munger = concreteTypeMunger.getMunger();
            if (munger == null) {
                continue;
            }
            if (munger.getKind() == ResolvedTypeMunger.Field) {
                if (munger.getSignature().getName().equals(nameStr)) {
                    return ITDInfo.create(concreteTypeMunger, false);
                }
            }

            if (munger.getKind() == ResolvedTypeMunger.Method) {
                // also need to compare parameters, but parameters
                // are expensive to calculate
                if (munger.getSignature().getName().equals(nameStr)) {
                    return ITDInfo.create(concreteTypeMunger, false);
                }
            }
        }
        return null;
    }

    private String createAJSignature(char[] targetTypeSignature) {
        char[] copy = new char[targetTypeSignature.length];
        System.arraycopy(targetTypeSignature, 0, copy, 0, copy.length);

//        // AspectJ parameterized signatures start with 'P'
//        boolean isGeneric = false;
//        for (int i = 0; i < copy.length; i++) {
//            if (copy[i] == '<') {
//                isGeneric = true;
//            }
//        }
//
//        if (isGeneric) {
//            copy[0] = 'P';
//        }
        CharOperation.replace(copy, '.', '/');
        return String.valueOf(copy);
    }

    private static String createJDTSignature(String ajSignature) {
        char[] copy = ajSignature.toCharArray();
        CharOperation.replace(copy, '/', '.');
        if (copy[0] == 'P') {
            copy[0] = 'L';
        }
        return String.valueOf(copy);
    }

    public ErasedTypeSignature getMethodTypeSignatures(String typeSignature, IProgramElement elt) {
        if (world == null) {
            return null;
        }
        // ensure '/' instead of '.'
        typeSignature = createAJSignature(typeSignature.toCharArray());

        ResolvedType type = world.resolve(UnresolvedType.forSignature(typeSignature));
        if (type == null || type.isMissing()) {
            return null;
        }
        List<ConcreteTypeMunger> itds = type.getInterTypeMungersIncludingSupers();
        ConcreteTypeMunger myMunger = null;
      for (ConcreteTypeMunger munger : itds) {
        if (equalSignatures(elt, munger)) {
          myMunger = munger;
          break;
        }
      }
        if (myMunger == null) {
            return null;
        }

        String returnTypeSig = myMunger.getSignature().getReturnType().getSignature();
        returnTypeSig = createJDTSignature(returnTypeSig);
//        returnTypeSig = Signature.toString(returnType);
        UnresolvedType[] parameterTypes = myMunger.getSignature().getParameterTypes();
        String[] parameterTypesStr = new String[parameterTypes.length];
        for (int i = 0; i < parameterTypes.length; i++) {
            parameterTypesStr[i] = parameterTypes[i].getErasureSignature();
            parameterTypesStr[i] = createJDTSignature(parameterTypesStr[i]);
            parameterTypesStr[i] = Signature.toString(parameterTypesStr[i]);
        }
        return new ErasedTypeSignature(returnTypeSig, parameterTypesStr, convertTypeParameters(myMunger.getSignature().getTypeVariables()));
    }

    private boolean equalSignatures(IProgramElement elt, ConcreteTypeMunger munger) {
        try {
            return equalNames(elt, munger) && equalParams(elt, munger);
        } catch (NullPointerException e) {
            // lots of things can be null
            return false;
        }
    }

    private boolean equalNames(IProgramElement elt, ConcreteTypeMunger munger) {
        ResolvedMember signature = munger.getSignature();
        return signature != null && (qualifiedElementName(signature)).equals(
                qualifiedElementName(elt));
    }

    private String qualifiedElementName(ResolvedMember signature) {
        return signature.getDeclaringType().getClassName() + "." + signature.getName();
    }

    private String qualifiedElementName(IProgramElement elt) {
//        String packageName = elt.getPackageName();
//        if (packageName != null && packageName.length() > 0) {
//            return packageName + "." + elt.getName();
//        } else {
            return elt.getName();
//        }
    }

    private boolean equalParams(IProgramElement elt, ConcreteTypeMunger munger) {
        UnresolvedType[] unresolvedTypes = munger.getSignature().getParameterTypes();
        List<char[]> eltTypes = elt.getParameterTypes();
        int unresolvedTypesLength = unresolvedTypes == null ? 0 : unresolvedTypes.length;
        int eltTypesLength = eltTypes == null ? 0 : eltTypes.size();
        if (unresolvedTypesLength != eltTypesLength) {
            return false;
        }
        for (int i = 0; i < unresolvedTypesLength; i++) {
            String eltParamType = new String(eltTypes.get(i));
            int genericStart = eltParamType.indexOf('<');
            if (genericStart > -1) {
                eltParamType = eltParamType.substring(0, genericStart);
            }
            String unresolvedTypeName = unresolvedTypes[i].getName();
            genericStart = unresolvedTypeName.indexOf('<');
            if (genericStart > -1) {
                unresolvedTypeName = unresolvedTypeName.substring(0, genericStart);
            }
            if (! unresolvedTypeName.equals(eltParamType)) {
                return false;
            }
        }
        return true;
    }
}
