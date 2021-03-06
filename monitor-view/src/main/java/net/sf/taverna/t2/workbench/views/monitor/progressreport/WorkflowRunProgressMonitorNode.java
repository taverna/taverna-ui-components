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
package net.sf.taverna.t2.workbench.views.monitor.progressreport;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.log4j.Logger;

import net.sf.taverna.t2.facade.WorkflowInstanceFacade;
import net.sf.taverna.t2.facade.WorkflowInstanceFacade.State;
import net.sf.taverna.t2.monitor.MonitorNode;
import net.sf.taverna.t2.monitor.MonitorableProperty;
import net.sf.taverna.t2.monitor.NoSuchPropertyException;
import net.sf.taverna.t2.workflowmodel.Processor;

/**
 * A <code>MonitorNode</code> that monitors changes on a workflow processor and 
 * updates the progress <code>TreeTable</code> when
 * <code>MonitorableProperty</code>s change.
 * 
 */
public class WorkflowRunProgressMonitorNode implements MonitorNode{

	private static Logger logger = Logger.getLogger(WorkflowRunProgressMonitorNode.class);
	
	private static final String STATUS_RUNNING = "Running";

	// Workflow processor that this monitor node refers to.
	private Processor processor;
	
	// Properties on a processor that we are monitoring: queueSize are
	// iterations (jobs) waiting to be executed on a processor (once all inputs have arrived);
	// sentJobs are currently executing iterations (jobs) on a processor (there 
	// may be several threads doing this in parallel); completedJobs are finished iterations;
	// error are number of iterations that produced an error.
	private int queueSize = 0;
	private int sentJobs = 0;
	private int completedJobs = 0;
	private int totalTranslatedErrors = 0;
	
	// Calculated from sentJobs and queueSize
	private int totalJobs = 0;
	
	private String[] owningProcess;

	// Monitorable properties for this workflow object
	private Set<MonitorableProperty<?>> properties;

	private boolean expired = false;

	// Creation date of this node
	private Date creationDate = new Date();

	// TreeTable that displays changes to the monitor nodes
	private WorkflowRunProgressTreeTable progressTreeTable;

	// Initially processor has not started yet
	private boolean processorStarted = false;

	// Facade of this run (needed to check the state of the run when setting node's state)
	private WorkflowInstanceFacade facade;

	private final WorkflowRunProgressMonitorNode parentMonitorNode;

	private Map<Processor, Set<List<String>>> failedChildInvocations = new HashMap<Processor, Set<List<String>>>();
	
	public WorkflowRunProgressMonitorNode(Processor processor,
			String[] owningProcess, Set<MonitorableProperty<?>> properties,
			WorkflowRunProgressTreeTable progressTreeTable, WorkflowInstanceFacade facade, WorkflowRunProgressMonitorNode parentMonitorNode) {
		this.properties = properties;
		this.processor = processor;
		this.owningProcess = owningProcess;
		this.progressTreeTable = progressTreeTable;
		this.facade = facade;
		this.parentMonitorNode = parentMonitorNode;		
	}

	public void addMonitorableProperty(MonitorableProperty<?> newProperty) {
		properties.add(newProperty);
	}

	public Date getCreationDate() {
		return creationDate;
	}

	public String[] getOwningProcess() {
		return owningProcess;
	}

	public Set<? extends MonitorableProperty<?>> getProperties() {
		return Collections.unmodifiableSet(properties);
	}

	public Object getWorkflowObject() {
		return processor;
	}

	public boolean hasExpired() {
		return expired;
	}

	public void expire() {
		expired = true;
	}

	/**
	 * Updates the progress TreeTable when changes to
	 * <code>MonitorableProperty</code>s are detected.
	 * 
	 */
	public synchronized void update() {
		boolean queueSizeChanged = false;
		boolean sentJobsChanged = false;
		boolean completedJobsChanged = false;
		boolean errorsChanged = false;

		for (MonitorableProperty<?> property : getProperties()) {
			String[] name = property.getName();
			if (name.length == 3) {
				if (name[0].equals("dispatch")) {
					if (name[1].equals("parallelize")) {
						if (name[2].equals("queuesize")) {
							try {
								int newQueueSize = (Integer) property
										.getValue();
								
								newQueueSize = newQueueSize == -1 ? 0 :
								  newQueueSize;
								 
								if (queueSize != newQueueSize) {
									queueSize = newQueueSize;
									queueSizeChanged = true;
								}
							} catch (NoSuchPropertyException e) {
							}
						} else if (name[2].equals("sentjobs")) {
							try {
								int newSentJobs = (Integer) property.getValue();
								if (sentJobs != newSentJobs) {
									sentJobs = newSentJobs;
									sentJobsChanged = true;
									if (!processorStarted) {
										if (progressTreeTable.getProcessorStartDate(processor) == null) {
											// Don't override it if already set
											progressTreeTable.setProcessorStartDate(processor, new Date());
										}
										// When we pause the run, sometimes we still get the event that
										// processor's sentJobs changed so the status will change to running
										// after the wf was paused which is not what we want.
										if (!facade.getState().equals(State.paused)){
											progressTreeTable.setProcessorStatus(processor, STATUS_RUNNING);
										}
										processorStarted = true;
									}
								}
							} catch (NoSuchPropertyException e) {
							}
						} else if (name[2].equals("completedjobs")) {
							try {
								int newCompletedJobs = (Integer) property
										.getValue();
								if (completedJobs != newCompletedJobs) {
									completedJobs = newCompletedJobs;
									completedJobsChanged = true;
								}
							} catch (NoSuchPropertyException e) {
							}
						}
					} else if (name[1].equals("errorbounce")) {
						if (name[2].equals("totalTranslated")) {
							try {
								int newErrors = (Integer) property.getValue();
								if (totalTranslatedErrors != newErrors) {
									totalTranslatedErrors = newErrors;
									errorsChanged = true;
								}
							} catch (NoSuchPropertyException e) {
							}
						}
						if (name[2].equals("translated")) {
							try {
								int directErrors = (Integer) property.getValue();
								if (directErrors > 0) {
									reportFailed();
								}
							} catch (NoSuchPropertyException e) {
							}
						}
						
					}
				}
			}
		}

		if (queueSizeChanged) {
			totalJobs = sentJobs + queueSize;
			progressTreeTable.setProcessorNumberOfQueuedIterations(processor,
					queueSize);
		}
		if (completedJobsChanged) {
			progressTreeTable.setProcessorNumberOfIterationsDoneSoFar(
					processor, completedJobs);
			totalJobs = sentJobs + queueSize;
		}
		if (errorsChanged && totalTranslatedErrors > 0) {
			progressTreeTable.setProcessorNumberOfFailedIterations(processor,
					totalTranslatedErrors);
		}
	}
	
	protected void reportFailed() {
		if (parentMonitorNode != null) {
			// Inform our parent that we in a way have failed as well
			parentMonitorNode.childFailed(this);
		}
	}
	
	private void reportChildFailed() {
		int numErrors = 0;
		synchronized(failedChildInvocations) {
			Set<List<String>> failed = failedChildInvocations.get(processor);		
			if (failed != null) {
				numErrors = failed.size();
			}
		}
		if (totalTranslatedErrors > 0) {
			// Should not happen, as nested workflows can't (yet) fail
			logger.warn("Node has both "
						+ totalTranslatedErrors
						+ " direct errors and "
						+ numErrors
						+ " child invocation errors, only reporting child invocation errors");
		}
		progressTreeTable.setProcessorNumberOfFailedIterations(processor,
			numErrors);		
		if (parentMonitorNode != null) {
			// Inform our parent that we in a way have failed as well
			parentMonitorNode.childFailed(this);
		}
	}

	protected synchronized void childFailed(
			WorkflowRunProgressMonitorNode child) {
		List<String> childOwningProcess = Arrays.asList(child.getOwningProcess());
		if (childOwningProcess.size() < 3) {
			logger.error("Unexpected length of child owning process " + child.getOwningProcess());
			return;
		}
		List<String> invocationProcess = childOwningProcess.subList(0, childOwningProcess.size()-3);
		
		boolean added;
		synchronized(failedChildInvocations) {
			Set<List<String>> failed = failedChildInvocations.get(processor);
			if (failed == null) {
				failed = new HashSet<List<String>>();
				failedChildInvocations.put(processor, failed);
			}
			added = failed.add(invocationProcess);
		}
		if (added) {
			reportChildFailed();
		}
	}

	public int getTotalNumberOfIterations() {
		return totalJobs;
	}

}
