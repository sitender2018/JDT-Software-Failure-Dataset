/*******************************************************************************
 * Copyright (c) 2000, 2003 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.jdt.internal.ui.text.correction;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.swt.graphics.Image;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.NamingConventions;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ArrayType;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Type;

import org.eclipse.jdt.internal.corext.codemanipulation.StubUtility;
import org.eclipse.jdt.internal.corext.dom.ASTNodeFactory;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.dom.ASTRewrite;
import org.eclipse.jdt.internal.corext.dom.ScopeAnalyzer;
import org.eclipse.jdt.internal.corext.textmanipulation.GroupDescription;

public class ChangeMethodSignatureProposal extends LinkedCorrectionProposal {
	
	public static interface ChangeDescription {
	}
	
	public static class SwapDescription implements ChangeDescription {
		int index;
		public SwapDescription(int index) {
			this.index= index;
		}
	}
	
	public static class RemoveDescription implements ChangeDescription {
	}
	
	private static class ModifyDescription implements ChangeDescription {
		String name;
		ITypeBinding type;
		SingleVariableDeclaration resultingNode;
		
		public ModifyDescription(ITypeBinding type, String name) {
			this.type= type;
			this.name= name;
		}		
	}
	
	public static class EditDescription extends ModifyDescription {
		public EditDescription(ITypeBinding type, String name) {
			super(type, name);
		}
	}
	
	public static class InsertDescription extends ModifyDescription {
		public InsertDescription(ITypeBinding type, String name) {
			super(type, name);
		}
	}	
		
	private ASTNode fNameNode;
	private IMethodBinding fSenderBinding;
	private ChangeDescription[] fParameterChanges;
		
	public ChangeMethodSignatureProposal(String label, ICompilationUnit targetCU, ASTNode nameNode, IMethodBinding binding, ChangeDescription[] changes, int relevance, Image image) {
		super(label, targetCU, null, relevance, image);
		
		fNameNode= nameNode;
		fSenderBinding= binding;
		fParameterChanges= changes;
	}
	
	protected ASTRewrite getRewrite() throws CoreException {
		CompilationUnit astRoot= (CompilationUnit) fNameNode.getRoot();
		ASTNode methodDecl= astRoot.findDeclaringNode(fSenderBinding);
		ASTNode newMethodDecl= null;
		boolean isInDifferentCU;
		if (methodDecl != null) {
			isInDifferentCU= false;
			newMethodDecl= methodDecl;
		} else {
			isInDifferentCU= true;
			astRoot= AST.parseCompilationUnit(getCompilationUnit(), true);
			newMethodDecl= astRoot.findDeclaringNode(fSenderBinding.getKey());
		}
		if (newMethodDecl instanceof MethodDeclaration) {
			ASTRewrite rewrite= new ASTRewrite(astRoot);
			modifySignature(rewrite, (MethodDeclaration) newMethodDecl, isInDifferentCU);
			return rewrite;
		}
		return null;
	}
	
	private void modifySignature(ASTRewrite rewrite, MethodDeclaration methodDecl, boolean isInDifferentCU) throws CoreException {
		GroupDescription selectionDescription= null;
		if (isInDifferentCU) {
			selectionDescription= new GroupDescription("selection"); //$NON-NLS-1$
			setSelectionDescription(selectionDescription);
		}
		
		List parameters= methodDecl.parameters();
		// create a copy to not loose the indexes
		SingleVariableDeclaration[] oldParameters= (SingleVariableDeclaration[]) parameters.toArray(new SingleVariableDeclaration[parameters.size()]);
		AST ast= methodDecl.getAST();
		int k= 0; // index over the oldParameters
		ArrayList usedNames= new ArrayList();
		boolean hasCreatedVariables= false;
		
		IVariableBinding[] declaredFields= fSenderBinding.getDeclaringClass().getDeclaredFields();
		for (int i= 0; i < declaredFields.length; i++) { // avoid to take parameter names that are equal to field names
			usedNames.add(declaredFields[i].getName());
		}		
		
		for (int i= 0; i < fParameterChanges.length; i++) {
			ChangeDescription curr= fParameterChanges[i];
			if (curr == null) {
				usedNames.add(oldParameters[k].getName().getIdentifier());
				k++;
			} else if (curr instanceof InsertDescription) {
				InsertDescription desc= (InsertDescription) curr;
				SingleVariableDeclaration newNode= ast.newSingleVariableDeclaration();
				String type= addImport(desc.type);
				newNode.setType(ASTNodeFactory.newType(ast, type));
				
				// remember to set name later
				desc.resultingNode= newNode;
				hasCreatedVariables= true;
				
				rewrite.markAsInserted(newNode, selectionDescription);
				parameters.add(i, newNode);
					
			} else if (curr instanceof RemoveDescription) {
				rewrite.markAsRemoved(oldParameters[k], selectionDescription);
				k++;
			} else if (curr instanceof EditDescription) {
				EditDescription desc= (EditDescription) curr;

				SingleVariableDeclaration newNode= ast.newSingleVariableDeclaration();
				String type= addImport(desc.type);
				newNode.setType(ASTNodeFactory.newType(ast, type));
				
				// remember to set name later
				desc.resultingNode= newNode;
				hasCreatedVariables= true;
				
				rewrite.markAsReplaced(oldParameters[k], newNode);
				
				k++;
			} else if (curr instanceof SwapDescription) {
				SingleVariableDeclaration decl1= oldParameters[k];
				SingleVariableDeclaration decl2= oldParameters[((SwapDescription) curr).index];
				
				rewrite.markAsReplaced(decl1, rewrite.createCopy(decl2), selectionDescription);
				rewrite.markAsReplaced(decl2, rewrite.createCopy(decl1), selectionDescription);
				
				usedNames.add(decl1.getName().getIdentifier());
				k++;	
			}
		}
		if (!hasCreatedVariables) {
			return;
		}
		
		if (methodDecl.getBody() != null) {
			// avoid take a name of a local variable inside
			CompilationUnit root= (CompilationUnit) methodDecl.getRoot();
			IBinding[] bindings= (new ScopeAnalyzer(root)).getDeclarationsAfter(methodDecl.getBody().getStartPosition(), ScopeAnalyzer.VARIABLES);
			for (int i= 0; i < bindings.length; i++) {
				usedNames.add(bindings[i].getName());
			}
		}
		
		fixupNames(rewrite, usedNames);
	}

	private void fixupNames(ASTRewrite rewrite, ArrayList usedNames) {
		AST ast= rewrite.getRootNode().getAST();
		// set names for new parameters
		for (int i= 0; i < fParameterChanges.length; i++) {
			ChangeDescription curr= fParameterChanges[i];
			if (curr instanceof ModifyDescription) {
				ModifyDescription desc= (ModifyDescription) curr;
				SingleVariableDeclaration var= desc.resultingNode;
				String suggestedName= desc.name;

				String typeKey= "param_type_" + i; //$NON-NLS-1$
				String nameKey= "param_name_" + i; //$NON-NLS-1$

				// collect name suggestions
				String favourite= null;
				String[] excludedNames= (String[]) usedNames.toArray(new String[usedNames.size()]);
				if (suggestedName != null) {
					favourite= StubUtility.guessArgumentName(getCompilationUnit().getJavaProject(), suggestedName, excludedNames);
					addLinkedModeProposal(nameKey, favourite);
				}
				Type type= var.getType();
				int dim= 0;
				if (type.isArrayType()) {
					dim= ((ArrayType) type).getDimensions();
					type= ((ArrayType) type).getElementType();
				}
				String[] suggestedNames=  NamingConventions.suggestArgumentNames(getCompilationUnit().getJavaProject(), "", ASTNodes.asString(type), dim, excludedNames); //$NON-NLS-1$
				for (int k= 0; k < suggestedNames.length; k++) {
					addLinkedModeProposal(nameKey, suggestedNames[k]);
				}
				if (favourite == null) {
					favourite= suggestedNames[0];
				}

				var.setName(ast.newSimpleName(favourite));
				usedNames.add(favourite);

				// collect type suggestions
				ITypeBinding[] bindings= ASTResolving.getRelaxingTypes(ast, desc.type);
				for (int k= 0; k < bindings.length; k++) {
					addLinkedModeProposal(typeKey, bindings[k]);
				}
			
				markAsLinked(rewrite, var.getType(), false, typeKey); //$NON-NLS-1$
				markAsLinked(rewrite, var.getName(), false, nameKey); //$NON-NLS-1$			
			}
		}
		markAsSelection(rewrite, fNameNode.getParent());
	}
}
