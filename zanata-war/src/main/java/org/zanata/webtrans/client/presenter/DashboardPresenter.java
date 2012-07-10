/*
 * Copyright 2012, Red Hat, Inc. and individual contributors as indicated by the
 * @author tags. See the copyright.txt file in the distribution for a full
 * listing of individual contributors.
 * 
 * This is free software; you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 * 
 * This software is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this software; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA, or see the FSF
 * site: http://www.fsf.org.
 */
package org.zanata.webtrans.client.presenter;

import net.customware.gwt.dispatch.client.DispatchAsync;
import net.customware.gwt.presenter.client.EventBus;
import net.customware.gwt.presenter.client.widget.WidgetDisplay;
import net.customware.gwt.presenter.client.widget.WidgetPresenter;

import org.zanata.webtrans.client.resources.WebTransMessages;
import org.zanata.webtrans.client.rpc.CachingDispatchAsync;
import org.zanata.webtrans.shared.model.WorkspaceContext;

import com.google.inject.Inject;

/**
*
* @author Alex Eng <a href="mailto:aeng@redhat.com">aeng@redhat.com</a>
*
*/
public class DashboardPresenter extends WidgetPresenter<DashboardPresenter.Display>
{

   public interface Display extends WidgetDisplay
   {
      
   }

   private final DispatchAsync dispatcher;
  

 

   @Inject
   public DashboardPresenter(final Display display, EventBus eventBus, WorkspaceContext workspaceContext, CachingDispatchAsync dispatcher, final WebTransMessages messages)
   {
      super(display, eventBus);
      this.dispatcher = dispatcher;
   }

   @Override
   protected void onBind()
   {
      
   }

   @Override
   protected void onUnbind()
   {
      // TODO Auto-generated method stub
      
   }
   @Override
   protected void onRevealDisplay()
   {
      // TODO Auto-generated method stub
      
   }
   
}
