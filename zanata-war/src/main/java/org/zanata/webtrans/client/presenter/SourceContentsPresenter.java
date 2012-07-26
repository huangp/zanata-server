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

import java.util.ArrayList;
import java.util.List;

import javax.inject.Provider;

import org.zanata.webtrans.client.editor.table.SourceContentsDisplay;
import org.zanata.webtrans.client.events.RequestValidationEvent;
import org.zanata.webtrans.client.ui.HasSelectableSource;
import org.zanata.webtrans.shared.model.TransUnit;
import com.allen_sauer.gwt.log.client.Log;
import com.google.common.collect.Lists;
import com.google.gwt.dom.client.Document;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.HasClickHandlers;
import com.google.inject.Inject;

import net.customware.gwt.presenter.client.EventBus;

/**
 * @author Alex Eng <a href="mailto:aeng@redhat.com">aeng@redhat.com</a>
 * 
 */
public class SourceContentsPresenter
{
   private HasSelectableSource selectedSource;

   private final EventBus eventBus;
   private SourceContentsDisplay display;
   private Provider<SourceContentsDisplay> displayProvider;
   private ArrayList<SourceContentsDisplay> displayList;

   @Inject
   public SourceContentsPresenter(final EventBus eventBus, Provider<SourceContentsDisplay> displayProvider)
   {
      this.eventBus = eventBus;
      this.displayProvider = displayProvider;
   }

   private final ClickHandler selectSourceHandler = new ClickHandler()
   {
      @Override
      public void onClick(ClickEvent event)
      {
         HasSelectableSource previousSource = selectedSource;
         selectedSource = (HasSelectableSource) event.getSource();

         if (previousSource != null)
         {
            previousSource.setSelected(false);
         }

         selectedSource.setSelected(true);

         Log.info("Selected source: " + selectedSource.getSource());
         eventBus.fireEvent(new RequestValidationEvent());

      }
   };

   /**
    * Select first source in the list when row is selected or reselect previous selected one
    *
    */
   public void setSelectedSource(int row)
   {
      SourceContentsDisplay sourceContentsView = displayList.get(row);
      if (sourceContentsView != null)
      {
         // after save as fuzzy re-render(will call
         // SourceContentsView.setValue(TransUnit) which cause re-creation of
         // SourcePanel list), we want to re-select the radio button
         List<HasSelectableSource> sourcePanelList = sourceContentsView.getSourcePanelList();
         for (HasSelectableSource sourcePanel : sourcePanelList)
         {
            if (selectedSource != null && selectedSource.getSource().equals(sourcePanel.getSource()))
            {
               fireClickEventToSelectSource(sourcePanel);
               return;
            }
         }
         //else by default it will select the first one
         fireClickEventToSelectSource(sourceContentsView.getSourcePanelList().get(0));
      }
   }

   private static void fireClickEventToSelectSource(HasSelectableSource sourcePanel)
   {
      ClickEvent.fireNativeEvent(Document.get().createClickEvent(0, 0, 0, 0, 0, false, false, false, false), sourcePanel);
   }

   public String getSelectedSource()
   {
      return selectedSource.getSource();
   }

   //TODO replace below setValue
   public SourceContentsDisplay getSourceContent(int row, TransUnit value)
   {
      SourceContentsDisplay sourceContentsView = displayList.get(row);

      sourceContentsView.setValue(value);

      List<HasSelectableSource> sourcePanelList = sourceContentsView.getSourcePanelList();

      for (HasClickHandlers sourcePanel : sourcePanelList)
      {
         sourcePanel.addClickHandler(selectSourceHandler);
      }
      return sourceContentsView;
   }

   public void initWidgets(int pageSize)
   {
      displayList = Lists.newArrayList();
      for (int i = 0; i < pageSize; i++)
      {
         SourceContentsDisplay display = displayProvider.get();
         displayList.add(display);
      }
   }

   public SourceContentsDisplay setValue(TransUnit value)
   {
      display.setValue(value);

      List<HasSelectableSource> sourcePanelList = display.getSourcePanelList();

      for (HasClickHandlers sourcePanel : sourcePanelList)
      {
         sourcePanel.addClickHandler(selectSourceHandler);
      }
      return display;
   }

   public SourceContentsDisplay getDisplay()
   {
      return display;
   }

   public void showData(List<TransUnit> transUnits)
   {
      for (int i = 0; i < displayList.size(); i++)
      {
         SourceContentsDisplay sourceContentsDisplay = displayList.get(i);
         sourceContentsDisplay.setValue(transUnits.get(i));
      }
   }

   public List<SourceContentsDisplay> getDisplays()
   {
      return displayList;
   }
}
