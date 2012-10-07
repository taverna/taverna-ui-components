/*******************************************************************************
 * Copyright (C) 2007 The University of Manchester
 *
 *  Modifications to the initial code base are copyright of their
 *  respective authors, or their employers as appropriate.
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2.1 of
 *  the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307
 ******************************************************************************/
package net.sf.taverna.t2.workbench.ui.actions;

import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.Set;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.KeyStroke;

import net.sf.taverna.t2.lang.observer.Observable;
import net.sf.taverna.t2.lang.observer.Observer;
import net.sf.taverna.t2.lang.ui.ModelMap;
import net.sf.taverna.t2.lang.ui.ModelMap.ModelMapEvent;
import net.sf.taverna.t2.workbench.ModelMapConstants;
import net.sf.taverna.t2.workbench.edits.EditManager;
import net.sf.taverna.t2.workbench.file.FileManager;
import net.sf.taverna.t2.workbench.icons.WorkbenchIcons;
import net.sf.taverna.t2.workbench.ui.DataflowSelectionManager;
import net.sf.taverna.t2.workbench.ui.DataflowSelectionMessage;
import net.sf.taverna.t2.workbench.ui.DataflowSelectionModel;
import net.sf.taverna.t2.workbench.ui.workflowview.WorkflowView;
import net.sf.taverna.t2.workbench.ui.zaria.WorkflowPerspective;

import org.apache.log4j.Logger;

import uk.org.taverna.scufl2.api.container.WorkflowBundle;
import uk.org.taverna.scufl2.api.core.Processor;
import uk.org.taverna.scufl2.api.core.Workflow;

/**
 * An action that copies the selected graph component.
 *
 * @author Alan R Williams
 *
 */
@SuppressWarnings("serial")
public class CutGraphComponentAction extends AbstractAction{

	private static ModelMap modelMap = ModelMap.getInstance();

	/* Perspective switch observer */
	private CurrentPerspectiveObserver perspectiveObserver = new CurrentPerspectiveObserver();

	/* Current workflow's selection model event observer.*/
	private Observer<DataflowSelectionMessage> workflowSelectionObserver = new DataflowSelectionObserver();

	private static Logger logger = Logger.getLogger(CutGraphComponentAction.class);

	private WorkflowBundle dataflow;
	private Processor processor;

	private final EditManager editManager;
	private final FileManager fileManager;
	private final DataflowSelectionManager dataflowSelectionManager;

	public CutGraphComponentAction(EditManager editManager, FileManager fileManager, final DataflowSelectionManager dataflowSelectionManager){
		super();
		this.editManager = editManager;
		this.fileManager = fileManager;
		this.dataflowSelectionManager = dataflowSelectionManager;
		putValue(SMALL_ICON, WorkbenchIcons.cutIcon);
		putValue(NAME, "Cut");
		putValue(SHORT_DESCRIPTION, "Cut selected component");
		putValue(Action.MNEMONIC_KEY, KeyEvent.VK_T);

		putValue(Action.ACCELERATOR_KEY,
				KeyStroke.getKeyStroke(KeyEvent.VK_X, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
		setEnabled(false);

	modelMap.addObserver(perspectiveObserver);

	modelMap.addObserver(new Observer<ModelMap.ModelMapEvent>() {
		public void notify(Observable<ModelMapEvent> sender, ModelMapEvent message) {
			if (message.getModelName().equals(ModelMapConstants.CURRENT_DATAFLOW)) {
				if (message.getNewModel() instanceof WorkflowBundle) {

					// Update the buttons status as current dataflow has changed
					updateStatus((WorkflowBundle) message.getNewModel());

					// Remove the workflow selection model listener from the previous (if any)
					// and add to the new workflow (if any)
					WorkflowBundle oldFlow = (WorkflowBundle) message.getOldModel();
					WorkflowBundle newFlow = (WorkflowBundle) message.getNewModel();
					if (oldFlow != null) {
						dataflowSelectionManager.getDataflowSelectionModel(oldFlow)
								.removeObserver(workflowSelectionObserver);
					}

					if (newFlow != null) {
						dataflowSelectionManager.getDataflowSelectionModel(newFlow)
								.addObserver(workflowSelectionObserver);
					}
				}
			}
		}
	});
}

/**
 * Check if action should be enabled or disabled and update its status.
 */
public void updateStatus(WorkflowBundle dataflow) {

	DataflowSelectionModel selectionModel = dataflowSelectionManager.getDataflowSelectionModel(dataflow);

	// List of all selected objects in the graph view
	Set<Object> selection = selectionModel.getSelection();

	if (selection.isEmpty()){
		setEnabled(false);
	}
	else{
		// Take the first selected item - we only support single selections anyway
		Object selected = selection.toArray()[0];
		if (selected instanceof Processor) {
			processor = (Processor) selected;
			this.dataflow = dataflow;
			setEnabled(true);
		}
		else{
			setEnabled(false);
		}
	}
}

	public void actionPerformed(ActionEvent e) {
		WorkflowView.cutProcessor(processor.getParent(), processor, null, editManager, dataflowSelectionManager);
	}

	/**
	 * Observes events on workflow Selection Manager, i.e. when a workflow
	 * node is selected in the graph view, and enables/disables this action accordingly.
	 */
	private final class DataflowSelectionObserver implements
			Observer<DataflowSelectionMessage> {

		public void notify(Observable<DataflowSelectionMessage> sender,
				DataflowSelectionMessage message) throws Exception {
			updateStatus(fileManager.getCurrentDataflow());
		}
	}

	/**
	 * Modify the enabled/disabled state of the action when ModelMapConstants.CURRENT_PERSPECTIVE has been
	 * modified (i.e. when perspective has been switched).
	 */
	public class CurrentPerspectiveObserver implements Observer<ModelMapEvent> {
		public void notify(Observable<ModelMapEvent> sender,
				ModelMapEvent message) throws Exception {
			if (message.getModelName().equals(
					ModelMapConstants.CURRENT_PERSPECTIVE)) {
				if (message.getNewModel() instanceof WorkflowPerspective) {
					updateStatus(fileManager.getCurrentDataflow());
				}
				else{
					setEnabled(false);
				}
			}
		}
	}
}
