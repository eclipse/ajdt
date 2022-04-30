/*******************************************************************************
 * Copyright (c) 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Luzius Meisser - initial implementation
 *******************************************************************************/
package org.eclipse.ajdt.core.javaelements;

import java.lang.reflect.Field;
import java.util.HashMap;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.internal.core.ASTHolderCUInfo;

/**
 * @author Luzius Meisser
 */
public class AJCompilationUnitInfo extends ASTHolderCUInfo {

	// get super's fields by reflection
	private static Field astLevelField;
	static {
        try {
            astLevelField = ASTHolderCUInfo.class.getDeclaredField("astLevel");
            astLevelField.setAccessible(true);
        } catch (NoSuchFieldException ignored) {
        }
	}

    private static Field resolveBindingsField;
    static {
        try {
            resolveBindingsField = ASTHolderCUInfo.class.getDeclaredField("resolveBindings");
            resolveBindingsField.setAccessible(true);
        } catch (NoSuchFieldException ignored) {
        }
    }

    private static Field reconcileFlagsField;
    static {
        try {
            reconcileFlagsField = ASTHolderCUInfo.class.getDeclaredField("reconcileFlags");
            reconcileFlagsField.setAccessible(true);
        } catch (NoSuchFieldException ignored) {
        }
    }

    private static Field problemsField;
    static {
        try {
            problemsField = ASTHolderCUInfo.class.getDeclaredField("problems");
            problemsField.setAccessible(true);
        } catch (NoSuchFieldException ignored) {
        }
    }

    private static Field astField;
    static {
        try {
            astField = ASTHolderCUInfo.class.getDeclaredField("ast");
            astField.setAccessible(true);
        } catch (NoSuchFieldException ignored) {
        }
    }


    /**
     * This field should be set only from within a synchronized block in
     * AJCompilationUnit.  If > 0, then original content mode, if <= 0 then
     * only show the transformed source
     */
    int originalContentMode = 0;


    public void setTimestamp(long stamp){
        this.timestamp = stamp;
    }


    public int getASTLevel() {
	    try {
            return astLevelField.getInt(this);
        } catch (SecurityException | IllegalAccessException | IllegalArgumentException ignored) {
        }
      return ICompilationUnit.NO_AST;
	}


    public boolean doResolveBindings() {
        try {
            return resolveBindingsField.getBoolean(this);
        } catch (SecurityException | IllegalAccessException | IllegalArgumentException ignored) {
        }
      return false;
	}

    public int getReconcileFlags() {
        try {
            return reconcileFlagsField.getInt(this);
        } catch (SecurityException | IllegalAccessException | IllegalArgumentException ignored) {
        }
      return ICompilationUnit.NO_AST;
	}


    @SuppressWarnings("rawtypes")
    public HashMap getProblems() {
        try {
            return (HashMap) problemsField.get(this);
        } catch (SecurityException | IllegalAccessException | IllegalArgumentException ignored) {
        }
      return null;
    }

    public void setAST(CompilationUnit cu) {
        try {
            astField.set(this, cu);
        } catch (SecurityException | IllegalAccessException | IllegalArgumentException ignored) {
        }

    }

}
