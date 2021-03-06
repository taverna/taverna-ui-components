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
package net.sf.taverna.t2.workbench.views.results.saveactions;

import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.prefs.Preferences;

import javax.swing.AbstractAction;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;

import net.sf.taverna.t2.invocation.InvocationContext;
import net.sf.taverna.t2.lang.results.ResultsUtils;
import net.sf.taverna.t2.lang.ui.ExtensionFileFilter;
import net.sf.taverna.t2.reference.ReferenceService;
import net.sf.taverna.t2.reference.T2Reference;
import net.sf.taverna.t2.workflowmodel.Dataflow;

import org.apache.log4j.Logger;

/**
 * Implementing classes are capable of storing a collection
 * of T2References held in a result map.
 * 
 * @author Tom Oinn
 * @author Alex Nenadic
 */
@SuppressWarnings("serial")
public abstract class SaveAllResultsSPI extends AbstractAction implements Comparable<SaveAllResultsSPI>{

	protected static Logger logger = Logger.getLogger(SaveAllResultsSPI.class);
	protected ReferenceService referenceService;
	protected InvocationContext context = null;
	protected Map<String, T2Reference> chosenReferences;
	protected JDialog dialog;
	private boolean isProvenanceEnabledForRun;
	private String runId;
	private Dataflow dataflow;
	
	public final String getRunId() {
		return runId;
	}

	public final Dataflow getDataflow() {
		return dataflow;
	}

	/**
	 * Returns the save result action implementing this interface. The returned
	 * action will be bound to the appropriate UI component used to trigger the
	 * save action.
	 */
	public abstract AbstractAction getAction();

	/**
	 * Sets the InvocationContext to be used to get the Reference Service to be
	 * used dereference the reference.
	 */
	public void setInvocationContext(InvocationContext context) {
		this.context = context;
	}

	/**
	 * The Map passed into this method contains the String -> T2Reference (port
	 * name to reference to value pairs) returned by the current set of results.
	 * The actual listener may well wish to display some kind of dialog, for
	 * example in the case of an Excel export plugin it would be reasonable to
	 * give the user some choice over where the results would be inserted into
	 * the sheet, and also where the generated file would be stored.
	 * <p>
	 * The parent parameter is optional and may be set to null, if not it is
	 * assumed to be the parent component in the UI which caused this action to
	 * be created, this allows save dialogs etc to be placed correctly.
	 */
	public void setChosenReferences(Map<String, T2Reference> chosenReferences) {
		this.chosenReferences = chosenReferences;
	}

	public void setParent(JDialog dialog) {
		this.dialog = dialog;
	}
	
	protected abstract String getFilter();

	/**
	 * Shows a standard save dialog and dumps the entire result
	 * set to the specified XML file.
	 */
	public void actionPerformed(ActionEvent e) {
		
		dialog.setVisible(false);
		
		JFileChooser fc = new JFileChooser();
		Preferences prefs = Preferences.userNodeForPackage(getClass());
		String curDir = prefs.get("currentDir", System.getProperty("user.home"));
		fc.resetChoosableFileFilters();
		if (getFilter() != null) {
		fc.setFileFilter(new ExtensionFileFilter(new String[]{getFilter()}));
		}
		fc.setCurrentDirectory(new File(curDir));
		fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
		
		boolean tryAgain = true;
		while (tryAgain) {
			tryAgain = false;
			int returnVal = fc.showSaveDialog(null);
			if (returnVal == JFileChooser.APPROVE_OPTION) {
				
				prefs.put("currentDir", fc.getCurrentDirectory().toString());
				File file = fc.getSelectedFile();
	
				if (getFilter() != null) {
					// If the user did not use the .xml extension for the file - append it to the file name now
					if (!file.getName().toLowerCase().endsWith("." + getFilter())) {
						String newFileName = file.getName() + "." + getFilter();
						file = new File(file.getParentFile(), newFileName);
					}
				}
				final File finalFile = file;
				
				if (file.exists()){ // File already exists
					// Ask the user if they want to overwrite the file
					String msg = file.getAbsolutePath() + " already exists. Do you want to overwrite it?";
					int ret = JOptionPane.showConfirmDialog(
							null, msg, "File already exists",
							JOptionPane.YES_NO_OPTION);
					
					if (ret == JOptionPane.YES_OPTION) {
						// Do this in separate thread to avoid hanging UI
						new Thread("SaveAllResults: Saving results to " + finalFile){
							public void run(){
								try {
									synchronized(chosenReferences){
										saveData(finalFile);
									}
								} catch (Exception ex) {
									JOptionPane.showMessageDialog(null, "Problem saving result data", "Save Result Error",
											JOptionPane.ERROR_MESSAGE);
									logger.error("SaveAllResults Error: Problem saving result data", ex);
								}	
							}
						}.start();
					}
					else{
						tryAgain = true;
					}
				}
				else{ // File does not already exist
					
					// Do this in separate thread to avoid hanging UI
					new Thread("SaveAllResults: Saving results to " + finalFile){
						public void run(){
							try {
								synchronized(chosenReferences){
									saveData(finalFile);
								}
							} catch (Exception ex) {
								JOptionPane.showMessageDialog(null, "Problem saving result data" + ex, "Save Result Error",
										JOptionPane.ERROR_MESSAGE);
								logger.error("SaveAllResults Error: Problem saving result data", ex);
							}
						}
					}.start();
				}
			}
		}  
	}
	
	protected abstract void saveData(File f) throws IOException;
	
	protected Object getObjectForName(String name) {
		Object result = null;
		if (chosenReferences.containsKey(name)) {
			result = ResultsUtils.convertReferenceToObject(chosenReferences.get(name),getReferenceService(),getContext());
		}
		if (result == null) {
			result = "null";
		}
		return result;
		
	}

	public void setRunId(String runId) {
		this.runId = runId;
	}

	public void setDataflow(Dataflow dataflow) {
		this.dataflow = dataflow;
	}

	public ReferenceService getReferenceService() {
		return referenceService;
	}

	public InvocationContext getContext() {
		return context;
	}

	public Map<String, T2Reference> getChosenReferences() {
		return chosenReferences;
	}

	public JDialog getDialog() {
		return dialog;
	}

	public void setProvenanceEnabledForRun(boolean isProvenanceEnabledForRun) {
		this.isProvenanceEnabledForRun = isProvenanceEnabledForRun;
	}

	public boolean isProvenanceEnabledForRun() {
		return isProvenanceEnabledForRun;
	}

	/**
	 * @param referenceService the referenceService to set
	 */
	public void setReferenceService(ReferenceService referenceService) {
		this.referenceService = referenceService;
	}
	

	@Override
	public int compareTo(SaveAllResultsSPI o) {
		// Could have just used Apache Commons' null-safe ObjectUtils.compare(this,o)
		// but we are using an old version of the commmons library which does not have this method
		
		// The actions names should not really be null, but we can never know
		String name = (String)this.getAction().getValue(AbstractAction.NAME);
		String name2 = (String)o.getAction().getValue(AbstractAction.NAME);

		return nullSafeStringComparator(name, name2);
	}
	
	/**
	 * See http://stackoverflow.com/questions/481813/how-to-simplify-a-null-safe-compareto-implementation
	 * @param one
	 * @param two
	 * @return
	 */
	public static int nullSafeStringComparator(final String one, final String two) {
	    if (one == null ^ two == null) {
	        return (one == null) ? -1 : 1;
	    }

	    if (one == null && two == null) {
	        return 0;
	    }

	    return one.compareToIgnoreCase(two);
	}
}

