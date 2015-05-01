/*
 * VoIP.ms SMS
 * Copyright (C) 2015 Michael Kourlas
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the
 * Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE
 * WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
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
    private boolean requestScrollToBottom;

    public FilterableListViewAdapter(ListView listView) {
        this.items = new ArrayList<T>();
        this.filterConstraint = "";
        this.listView = listView;
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

    public abstract class FilterableAdapterFilter extends Filter {
        @SuppressWarnings("unchecked")
        @Override
        protected void publishResults(CharSequence constraint, FilterResults results) {
            filterConstraint = constraint.toString();
            items.clear();
            items.addAll((List<T>) results.values);
            notifyDataSetChanged();

            if (requestScrollToBottom) {
                listView.smoothScrollToPosition(listView.getCount() - 1);
                requestScrollToBottom = false;
            }
        }
    }
}
