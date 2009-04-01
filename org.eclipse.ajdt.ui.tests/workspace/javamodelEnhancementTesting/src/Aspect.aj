/*******************************************************************************
 * Copyright (c) 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors
 *     Luzius Meisser - initial implementation
 *******************************************************************************/



/**
 * 
 * Please do not edit this file.
 * 
 * @author Luzius Meisser
 */
public privileged abstract aspect Aspect percflow(within(C)){
	
	public int C.x;
	
	public void C.getX(){
		return C.x;
	}
	
	int y;
	
	//comment
	pointcut myPoinctut(): execution(* *(..));
	
	/**
	 * comment 2
	 */
	declare warning: myPointcut(): "warning";
	
	after() throwing(Exception e): myPointcut(){
		System.out.prinln("test");
	}

}