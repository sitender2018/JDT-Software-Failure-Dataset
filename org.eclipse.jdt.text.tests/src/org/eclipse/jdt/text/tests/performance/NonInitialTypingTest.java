/*******************************************************************************
 * Copyright (c) 2000, 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.jdt.text.tests.performance;

import junit.framework.TestCase;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;

import org.eclipse.test.performance.Performance;
import org.eclipse.test.performance.PerformanceMeter;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.TextSelection;

import org.eclipse.ui.PartInitException;
import org.eclipse.ui.texteditor.ITextEditor;

import org.eclipse.jdt.text.tests.performance.eval.Evaluator;
import org.eclipse.jdt.text.tests.performance.eval.IEvaluator;

/**
 * Measures the time to type in one single method into a large Java class
 * @since 3.1
 */
public class NonInitialTypingTest extends TestCase {
	
	private static final String FILE= "org.eclipse.swt/Eclipse SWT Custom Widgets/common/org/eclipse/swt/custom/StyledText.java";

	private PerformanceMeterFactory fPerformanceMeterFactory= Performance.createPerformanceMeterFactory();

	private ITextEditor fEditor;
	
	private static final char[] METHOD= ("public int foobar(int iParam, Object oParam) {\r" +
			"return 42;\r" +
			"}\r").toCharArray();

	private PerformanceMeter fMeter;

	private KeyboardProbe fKeyboardProbe;

	private IEvaluator fEvaluator;

	protected void setUp() throws PartInitException, BadLocationException {
		EditorTestHelper.runEventQueue();
		fEditor= (ITextEditor) EditorTestHelper.openInEditor(ResourceTestHelper.findFile(FILE), true);
		// dirty editor to avoid initial dirtying / validate edit costs
		fKeyboardProbe= new KeyboardProbe();
		dirtyEditor();
		fMeter= fPerformanceMeterFactory.createPerformanceMeter(this);
		fEvaluator= Evaluator.getDefaultEvaluator();

		int offset= getInsertPosition();
		fEditor.getSelectionProvider().setSelection(new TextSelection(offset, 0));
		EditorTestHelper.runEventQueue();
		sleep(1000);
	}
	
	private void dirtyEditor() {
		fEditor.getSelectionProvider().setSelection(new TextSelection(0, 0));
		EditorTestHelper.runEventQueue();
		sleep(1000);
		
		Display display= SWTEventHelper.getActiveDisplay();
		fKeyboardProbe.pressChar('{', display);
		EditorTestHelper.runEventQueue();
		SWTEventHelper.pressKeyCode(display, SWT.BS);
		sleep(1000);
	}

	protected void tearDown() throws Exception {
		sleep(1000);
		EditorTestHelper.revertEditor(fEditor, true);
		EditorTestHelper.closeAllEditors();
	}

	public void testTypeAMethod() {
		Display display= SWTEventHelper.getActiveDisplay();
		
		fMeter.start();
		for (int i= 0; i < METHOD.length; i++) {
			fKeyboardProbe.pressChar(METHOD[i], display);
			EditorTestHelper.runEventQueue();
		}
		fMeter.stop();
		fMeter.commit();
		fEvaluator.evaluate(fMeter.getSessionData());
	}

	private synchronized void sleep(int time) {
		try {
			wait(time);
		} catch (InterruptedException e) {
		}
	}
	
	private int getInsertPosition() throws BadLocationException {
		IDocument document= EditorTestHelper.getDocument(fEditor);
		int lines= document.getNumberOfLines();
		int offset= document.getLineOffset(lines - 2);
		return offset;
	}
}
