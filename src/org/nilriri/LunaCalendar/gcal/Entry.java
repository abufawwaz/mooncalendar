/*
 * Copyright (c) 2010 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package org.nilriri.LunaCalendar.gcal;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.nilriri.LunaCalendar.tools.Common;

import android.util.Log;

import com.google.api.client.googleapis.xml.atom.AtomPatchRelativeToOriginalContent;
import com.google.api.client.googleapis.xml.atom.GoogleAtom;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.util.DataUtil;
import com.google.api.client.util.Key;
import com.google.api.client.xml.atom.AtomContent;

/**
 * @author Yaniv Inbar
 */
public class Entry implements Cloneable {

    @Key
    public String id;

    @Key
    public String summary;

    @Key
    public String title;

    @Key
    public String updated;

    @Key("@gd:etag")
    public String etag;

    @Key("link")
    public List<Link> links = new ArrayList<Link>();

    @Override
    protected Entry clone() {
        return DataUtil.clone(this);
    }

    public int executeDelete(HttpTransport transport) throws IOException {
        HttpRequest request = transport.buildDeleteRequest();

        /*
        if ("".equals(this.etag) || this.etag == null) {
            // guid�� ���� ��� �̺�Ʈ�� update�Ѵ�.            
            request.headers.ifNoneMatch = "";
        } else {
            request.headers.ifMatch = this.etag.trim();//.replace("\"", "");
            //request.headers.etag = this.etag.trim();//.replace("\"", "");
        }
        */

        if ("".equals(getEditLink()) || getEditLink() == null) {
            return 0;
        }

        request.headers.ifMatch = this.etag;
        request.setUrl(getEditLink());

        /*        
                AtomContent content = new AtomContent();
                content.namespaceDictionary = Util.DICTIONARY;
                content.entry = this;
                request.content = content;
        */

        Log.d(Common.TAG, "executeDelete request.content=" + request.content);
        Log.d(Common.TAG, "executeDelete request.url=" + request.url);
        Log.d(Common.TAG, "executeUpdate request.header=" + request.headers);
        Log.d(Common.TAG, "executeDelete ETAG=" + this.etag);

        //request.execute().ignore();
        HttpResponse response = RedirectHandler.execute(request);//.ignore();

        //412 Precondition Failed

        if (response.statusCode == 412) {
            Log.d(Common.TAG, "412 executeDelete=" + response.parseAsString());
            Log.d(Common.TAG, "412 executeDelete=" + response.headers);
        } else if (response.statusCode == 403) {
            Log.d(Common.TAG, "403 executeDelete=" + response.parseAsString());
            Log.d(Common.TAG, "403 executeDelete=" + response.headers);
        }

        return response.statusCode;

    }

    public Entry executeInsert(HttpTransport transport, CalendarUrl url) throws IOException {

        HttpRequest request = transport.buildPostRequest();

        request.url = url;
        AtomContent content = new AtomContent();
        content.namespaceDictionary = Util.DICTIONARY;
        content.entry = this;
        request.content = content;

        Log.d(Common.TAG, "executeInsert request.content=" + request.content);
        Log.d(Common.TAG, "executeInsert request.url=" + request.url);

        HttpResponse response = RedirectHandler.execute(request);

        Log.d(Common.TAG, "statusCode=" + response.statusCode);

        // HTTP �����ڵ尡 200 OK.�� ��� Google UID�� UPDATE�Ѵ�.
        // HTTP �����ڵ尡 201 CREATED.�� ��� Google UID�� UPDATE�Ѵ�.
        if (200 == response.statusCode || 201 == response.statusCode) {
            return response.parseAs(getClass());
        } else {
            Log.d(Common.TAG, "res=" + response.parseAsString());
            return this;
        }
    }

    static Entry executeGetOriginalEntry(HttpTransport transport, CalendarUrl url, Class<? extends Entry> entryClass) throws IOException {
        url.fields = GoogleAtom.getFieldsFor(entryClass);
        HttpRequest request = transport.buildGetRequest();
        request.url = url;

        HttpResponse response = RedirectHandler.execute(request);
        //Log.d("~~~~~~~~~~~~~~~~", "response.toString()=" + response.parseAsString());

        return response.parseAs(entryClass);

        // return RedirectHandler.execute(request).parseAs(feedClass);
    }

    public Entry executePatchRelativeToOriginal(HttpTransport transport, Entry original) throws IOException {
        HttpRequest request = transport.buildPatchRequest();
        request.setUrl(getEditLink());

        AtomPatchRelativeToOriginalContent content = new AtomPatchRelativeToOriginalContent();
        content.namespaceDictionary = Util.DICTIONARY;
        content.originalEntry = original;
        content.patchedEntry = this;

        request.content = content;

        return RedirectHandler.execute(request).parseAs(getClass());
    }

    public String getEditLink() {
        String link = Link.find(links, "edit");
        if ("".equals(link) || link == null) {
            link = (this.id == null ? "" :this.id);
            //link = Link.find(links, "self");
        }

        return link;
    }

    public String getSelfLink() {
        return Link.find(links, "self");
    }
}
