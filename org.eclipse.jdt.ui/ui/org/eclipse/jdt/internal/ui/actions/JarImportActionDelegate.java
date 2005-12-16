/*******************************************************************************
 * Copyright (c) 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.actions;

import org.eclipse.jface.action.IAction;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.wizard.WizardDialog;

import org.eclipse.ui.IImportWizard;
import org.eclipse.ui.IObjectActionDelegate;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;

import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.jarimport.JarImportWizard;

/**
 * Action delegate for the jar import action.
 * 
 * @since 3.2
 */
public final class JarImportActionDelegate implements IObjectActionDelegate {

	/** The wizard height */
	private static final int SIZING_WIZARD_HEIGHT= 520;

	/** The wizard width */
	private static final int SIZING_WIZARD_WIDTH= 470;

	/** The structured selection, or <code>null</code> */
	private IStructuredSelection fSelection= null;

	/** The active workbench part, or <code>null</code> */
	private IWorkbenchPart fWorkbenchPart= null;

	/**
	 * {@inheritDoc}
	 */
	public void run(final IAction action) {
		if (fWorkbenchPart == null || fSelection == null)
			return;
		final IImportWizard wizard= new JarImportWizard(false);
		final IWorkbenchWindow window= fWorkbenchPart.getSite().getWorkbenchWindow();
		wizard.init(window.getWorkbench(), fSelection);
		final WizardDialog dialog= new WizardDialog(window.getShell(), wizard);
		dialog.create();
		dialog.getShell().setSize(Math.max(SIZING_WIZARD_WIDTH, dialog.getShell().getSize().x), SIZING_WIZARD_HEIGHT);
		PlatformUI.getWorkbench().getHelpSystem().setHelp(dialog.getShell(), IJavaHelpContextIds.JARIMPORT_WIZARD_PAGE);
		dialog.open();
	}

	/**
	 * {@inheritDoc}
	 */
	public void selectionChanged(final IAction action, final ISelection selection) {
		if (selection instanceof IStructuredSelection) {
			final IStructuredSelection structured= (IStructuredSelection) selection;
			if (structured.size() == 1) {
				final Object element= structured.getFirstElement();
				if (element instanceof IPackageFragmentRoot) {
					final IPackageFragmentRoot root= (IPackageFragmentRoot) element;
					try {
						if (JarImportWizard.isValidPackageFragmentRoot(root) && JarImportWizard.isValidJavaProject(root.getJavaProject()))
							fSelection= structured;
					} catch (JavaModelException exception) {
						JavaPlugin.log(exception);
					}
				}
			}
		} else
			fSelection= null;
		action.setEnabled(fSelection != null);
	}

	/**
	 * {@inheritDoc}
	 */
	public void setActivePart(final IAction action, final IWorkbenchPart part) {
		fWorkbenchPart= part;
	}
}