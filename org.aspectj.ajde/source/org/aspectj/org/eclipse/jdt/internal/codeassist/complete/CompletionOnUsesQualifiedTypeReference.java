/*******************************************************************************
 * Copyright (c) 2016 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     
 *******************************************************************************/package org.aspectj.org.eclipse.jdt.internal.codeassist.complete;

public class CompletionOnUsesQualifiedTypeReference extends CompletionOnQualifiedTypeReference {

	public CompletionOnUsesQualifiedTypeReference(char[][] previousIdentifiers, char[] completionIdentifier,
			long[] positions) {
		super(previousIdentifiers, completionIdentifier, positions);
	}

}
