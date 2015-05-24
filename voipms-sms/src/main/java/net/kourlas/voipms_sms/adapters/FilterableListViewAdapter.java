/*
 * VoIP.ms SMS
 * Copyright (C) 2015 Michael Kourlas and other contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.kourlas.voipms_sms.adapters;

import android.widget.BaseAdapter;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ListView;

import java.util.ArrayList;
import java.util.List;

public abstract class FilterableListViewAdapter<T> extends BaseAdapter implements Filterable {

    private final List<T> items;
    private final ListView listView;
    private String filterConstraint;
    private boolean requestScrollToTop;
    private boolean requestScrollToBottom;

    FilterableListViewAdapter(ListView listView) {
        this.items = new ArrayList<T>();
        this.filterConstraint = "";
        this.listView = listView;
    }

    public void requestScrollToTop() {
        requestScrollToTop = true;
    }

    public void requestScrollToBottom() {
        requestScrollToBottom = true;
    }

    public void refresh() {
        getFilter().filter(filterConstraint);
    }

    public void refresh(String newFilterConstraint) {
        getFilter().filter(newFilterConstraint);
    }

    @Override
    public int getCount() {
        return items.size();
    }

    @Override
    public Object getItem(int position) {
        return items.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    abstract class FilterableAdapterFilter extends Filter {
        @SuppressWarnings("unchecked")
        @Override
        protected void publishResults(CharSequence constraint, FilterResults results) {
            filterConstraint = constraint.toString();
            items.clear();
            items.addAll((List<T>) results.values);
            notifyDataSetChanged();

            if (requestScrollToTop) {
                if (items.size() >= 1) {
                    listView.smoothScrollToPosition(0);
                }
                requestScrollToTop = false;
            }
            if (requestScrollToBottom) {
                listView.smoothScrollToPosition(listView.getCount() - 1);
                requestScrollToBottom = false;
            }
        }
    }
}
