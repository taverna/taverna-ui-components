/*******************************************************************************
 * Copyright (C) 2007-2009 The University of Manchester   
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
package net.sf.taverna.t2.workbench.ui.servicepanel;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;

import javax.swing.tree.DefaultMutableTreeNode;

import net.sf.taverna.t2.servicedescriptions.ServiceDescription;
import net.sf.taverna.t2.workbench.ui.servicepanel.tree.Filter;

public class ServiceFilter implements Filter {

	private String filterString;
	private boolean superseded;
	private String[] filterLowerCaseSplit;

	public ServiceFilter(String filterString) {
		this.filterString = filterString;
		this.filterLowerCaseSplit = filterString.toLowerCase().split(" ");
		this.superseded = false;
	}

	@SuppressWarnings("unchecked")
	private boolean basicFilter(DefaultMutableTreeNode node) {
		if (filterString.equals("")) {
			return true;
		}
		if (node.getUserObject() instanceof ServiceDescription) {
			ServiceDescription serviceDescription = (ServiceDescription) node
					.getUserObject();
			search: for (String searchTerm : filterLowerCaseSplit) {
				if (superseded) {
					return false;
				}
				String[] typeSplit = searchTerm.split(":", 2);
				String type;
				String keyword;
				if (typeSplit.length == 2) {
					type = typeSplit[0];
					keyword = typeSplit[1];
				} else {
					type = null;
					keyword = searchTerm;
				}
				try {
					BeanInfo beanInfo = Introspector
							.getBeanInfo(serviceDescription.getClass());
					for (PropertyDescriptor property : beanInfo
							.getPropertyDescriptors()) {
						if (superseded) {
							return false;
						}
						if (type == null && !property.isHidden()
								&& !property.isExpert()
								|| property.getName().equalsIgnoreCase(type)) {
							Object readProperty = property.getReadMethod()
									.invoke(serviceDescription, new Object[0]);
							if (readProperty == null) {
								continue;
							}
							if (readProperty.toString().toLowerCase().contains(
									keyword)) {
								continue search;
							} else {
								// Dig deeper?
							}
						}
					}
					return false;
				} catch (IntrospectionException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (IllegalArgumentException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (IllegalAccessException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (InvocationTargetException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				return false;
			}
			return true;
		}
		for (String searchString : filterLowerCaseSplit) {
			if (!node.getUserObject().toString().toLowerCase().contains(
					searchString)) {
				return false;
			}
		}
		return true;
	}

	public boolean pass(DefaultMutableTreeNode node) {
		return basicFilter(node);
	}

	public String filterRepresentation(String original) {
		return original;
	}

	/**
	 * @return the superseded
	 */
	public boolean isSuperseded() {
		return superseded;
	}

	/**
	 * @param superseded
	 *            the superseded to set
	 */
	public void setSuperseded(boolean superseded) {
		this.superseded = superseded;
	}

}