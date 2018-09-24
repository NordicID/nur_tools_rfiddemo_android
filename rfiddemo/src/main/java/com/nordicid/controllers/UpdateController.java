package com.nordicid.controllers;

import android.os.AsyncTask;
import android.util.Log;
import com.nordicid.helpers.UpdateContainer;
import com.nordicid.rfiddemo.Main;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public abstract class UpdateController {

    protected static String LOG_TAG = "Update Controller";
    protected String mAppUpdateSource;
    protected String mBldrUpdateSource;
    protected String hwType = null;
    protected String appVersion = null;
    protected String bldrVersion = null;
    protected String mFilePath = null;
    protected String mAppUpdateVersion = null;
    protected String mBldrUpdateVersion = null;

    public void setFilePath(String filePath){
        mFilePath = filePath;
    }

    public String getFilePath() { return mFilePath; }

    public boolean isFileSet(){
        return mFilePath != null;
    }

    public void setHWType(String module){
        hwType = module;
    }

    public void setAPPVersion(String version){
        appVersion = version;
    }

    public void setBldrVersion(String version){
        bldrVersion = version;
    }

    public void setAppSource(String source){
        mAppUpdateSource = source;
    }

    public void setBldrSource(String version){
        bldrVersion = version;
    }

    public String getAvailableAppUpdateVerion(){
        return mAppUpdateVersion;
    }

    public String getAvailableBldrUpdateVerion(){ return mBldrUpdateVersion; }

    private String Stream2String(InputStream is) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line).append('\n');
        }
        is.close();
        return sb.toString();
    }

    /** Async task running GET request
     *  Should wait for completion before doing anything else
     *  Returns Json String
     **/
    private class ExecuteGetOperation extends AsyncTask<Boolean, Void, String>{
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected String doInBackground(Boolean... params) {
            try {
                Log.i("VER", "target=" + params[0] + "appSrc="+mAppUpdateSource + " BldrSrc=" + mBldrUpdateSource);
                URL srcURL = new URL((params[0]) ? mAppUpdateSource : mBldrUpdateSource);
                HttpURLConnection connection = (HttpURLConnection) srcURL.openConnection();
                connection.setRequestMethod("GET");
                InputStream in = new BufferedInputStream(connection.getInputStream());
                return Stream2String(in);
            } catch (Exception e){
                Log.e(LOG_TAG, e.getMessage());
            }
            return null;
        }

        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);
        }
    }

    /**
     * Async task downloading the file to Cache
     * @return
     */
    private class ExecuteDownloadOperation extends AsyncTask<UpdateContainer, Void, String>{

        private String getFileNameFromURL(String url) {
            if (url == null) {
                return "";
            }
            try {
                URL resource = new URL(url);
                String host = resource.getHost();
                if (host.length() > 0 && url.endsWith(host)) {
                    // handle ...example.com
                    return "";
                }
            }
            catch(MalformedURLException e) {
                return "";
            }

            int startIndex = url.lastIndexOf('/') + 1;
            int length = url.length();

            // find end index for ?
            int lastQMPos = url.lastIndexOf('?');
            if (lastQMPos == -1) {
                lastQMPos = length;
            }

            // find end index for #
            int lastHashPos = url.lastIndexOf('#');
            if (lastHashPos == -1) {
                lastHashPos = length;
            }

            // calculate the end index
            int endIndex = Math.min(lastQMPos, lastHashPos);
            return url.substring(startIndex, endIndex);
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected String doInBackground(UpdateContainer... params) {

            int cnt=0;

            try{
                if(params[0] != null) {
                    URL fileUrl = new URL(params[0].url);
                    String filename=getFileNameFromURL(params[0].url);
                    if(filename != "") params[0].name = filename;

                    URLConnection connection = fileUrl.openConnection();

                    InputStream sin = connection.getInputStream();
                    BufferedInputStream bis = new BufferedInputStream(sin, 1024);

                    File file = new File(Main.getInstance().getFilesDir(), params[0].name);

                    if(file.exists())
                        file.delete();
                    file.createNewFile();

                    FileOutputStream fos = new FileOutputStream(file);
                    byte[] buffer = new byte[1024];

                    int len;
                    while ((len = sin.read(buffer)) != -1)
                    {
                        cnt+=len;
                        fos.write(buffer, 0, len);
                    }

                    fos.flush();
                    fos.close();
                    sin.close();

                    return params[0].name;
                }
            } catch (Exception e){
                // this ide is retarded putting a comment makes the catch block non empty
            }
            return null;
        }

        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);
        }
    }

    /**
     * Compares two version strings. Simply discards other char than numeric and made conversion
     *
     *
     * @note Works only with 3 digits separated with dots like: "5.0.1"
     *
     * @param str1 a string of ordinal numbers separated by decimal points.
     * @param str2 a string of ordinal numbers separated by decimal points.
     * @return The result is a negative integer if str1 is _numerically_ less than str2.
     *         The result is a positive integer if str1 is _numerically_ greater than str2.
     *         The result is zero if the strings are _numerically_ equal.
     */
    private int versionCompare(String str1, String str2) {
        String s1,s2;

        s1 = str1.replaceAll("[^0-9]", "");
        s2 = str2.replaceAll("[^0-9]", "");

        try {
            if (Integer.valueOf(s1) < Integer.valueOf(s2)) return -1;
            else if (Integer.valueOf(s1) == Integer.valueOf(s2)) return 0;
            else return 1;
        }
        catch (Exception e)
        {
            //Should not happen but..
            return 0;
        }
    }

    /**
     * Compares two version strings.
     *
     * Use this instead of String.compareTo() for a non-lexicographical
     * comparison that works for version strings. e.g. "1.10".compareTo("1.6").
     *
     * @note It does not work if "1.10" is supposed to be equal to "1.10.0".
     *
     * @param str1 a string of ordinal numbers separated by decimal points.
     * @param str2 a string of ordinal numbers separated by decimal points.
     * @return The result is a negative integer if str1 is _numerically_ less than str2.
     *         The result is a positive integer if str1 is _numerically_ greater than str2.
     *         The result is zero if the strings are _numerically_ equal.
     */
    public static int versionCompareNUR(String str1, String str2) {

        String s1,s2;
        s1 = str1.replaceAll("[^0-9.]", "");
        s2 = str2.replaceAll("[^0-9.]", "");

        String[] vals1 = s1.split("\\.");
        String[] vals2 = s2.split("\\.");
        int i = 0;
        // set index to first non-equal ordinal or length of shortest version string
        while (i < vals1.length && i < vals2.length && vals1[i].equals(vals2[i])) {
            i++;
        }
        // compare first non-equal ordinal number
        if (i < vals1.length && i < vals2.length) {
            int diff = Integer.valueOf(vals1[i]).compareTo(Integer.valueOf(vals2[i]));
            return Integer.signum(diff);
        }
        // the strings are equal or one string is a substring of the other
        // e.g. "1.2.3" = "1.2.3" or "1.2.3" < "1.2.3.4"
        return Integer.signum(vals1.length - vals2.length);
    }

    private boolean IsValidHW(String [] hw)
    {
        for (int x = 0; x < hw.length; x++) {
            if (hwType.contains(hw[x])) {
                return true;
            }
            /*if(hwType.startsWith("EXA"))
            {
                if(hw[x].startsWith("EXA"))
                    return true;
            }
            else if(hwType.startsWith("NUR-05WL2"))
            {
                if(hw[x].startsWith("NUR-05WL2"))
                    return true;
            }
            else if(hwType.startsWith("NUR-10W"))
            {
                if(hw[x].startsWith("NUR-10W"))
                    return true;
            }
            else if(hwType.startsWith("NUR2-1W"))
            {
                if(hw[x].startsWith("NUR2-1W"))
                    return true;
            }*/
        }

        return false;
    }

    private List<UpdateContainer> fetchAvailableUpdates(boolean target){

        int verc=0;

        List<UpdateContainer> availableUpdates = new ArrayList<>();
        if (hwType == null)
            return availableUpdates;

        try{
            /* invoke async task and wait for result */
            String data = new ExecuteGetOperation().execute(target).get();
            //Log.i("VER","JSON="+data);

            if(data != null){
                JSONArray firmwares = new JSONObject(data).getJSONArray("firmwares");
                Log.i("VER","FW's" + firmwares.length());
                for ( int it = 0; it < firmwares.length(); it++ ) {
                    JSONObject firmware = firmwares.getJSONObject(it);
                    UpdateContainer fwUpdate = new UpdateContainer();
                    fwUpdate.buildtime = new java.util.Date(Long.parseLong(firmware.getString("buildtime")) *1000).toString();
                    fwUpdate.md5 = firmware.getString("md5");
                    fwUpdate.url = firmware.getString("url");
                    fwUpdate.version = firmware.getString("version");
                    fwUpdate.name = firmware.getString("name");
                    /** Can be taken out
                     *  Only usefull for testing hardware match
                     **/
                    JSONArray hardware = firmware.getJSONArray("hw");
                    fwUpdate.hw = new String[hardware.length()];

                    for (int x = 0; x < hardware.length(); x++) {
                        fwUpdate.hw[x] = hardware.getString(x);
                        Log.i("VER","HW=" + fwUpdate.hw[x]);
                    }

                    if(!IsValidHW(fwUpdate.hw)) {
                        continue;
                    }

                    Log.i("VER","ValidHW=" + hwType);

                    if(hwType.startsWith("EXA")) {
                        if (target == false)
                            verc = versionCompare(bldrVersion, fwUpdate.version);
                        else
                            verc = versionCompare(appVersion, fwUpdate.version);

                        Log.i("VER", fwUpdate.name + "EXA HW=" + hwType + " appVer=" + appVersion + " VERC=" + verc);
                    }
                    else
                    {
                        if (target == false)
                            verc = versionCompareNUR(bldrVersion, fwUpdate.version);
                        else
                            verc = versionCompareNUR(appVersion, fwUpdate.version);

                        Log.i("VER", fwUpdate.name + "NUR HW=" + hwType + " appVer=" + appVersion + "fwVer=" + fwUpdate.version + " VERC=" + verc);
                    }

                    //if current version is same or higher, this item will be not shown
                    if(verc >= 0) {
                        continue;
                    }

                    boolean do_not_include=false;
                    if(hwType.startsWith("EXA")) {
                        //Special treatment for EXA FW
                        for (int ite = 0; ite < hardware.length(); ite++) {
                            fwUpdate.hw[ite] = hardware.getString(ite);
                            if (target == true) {
                                verc = versionCompare(appVersion, "5.0.0");
                                Log.i("VER", "fwUpdateVer=" + fwUpdate.version + " appVer=" + appVersion + " VERC=" + verc + " HW=" + fwUpdate.hw[ite]);
                                if (verc == -1) {
                                    //Current version = 2.x.x
                                    //We don't want to show app 5.x.x
                                    if (fwUpdate.hw[ite].compareTo("EXA5") == 0)
                                        continue; //We want this. update packet from 2.x.x to 5.x.x
                                    if (versionCompare(fwUpdate.version, "5.0.0") == -1)
                                        continue; //We want also show apps below 5.x.x
                                    else
                                        do_not_include = true; //do not show if current ver 2.x.x and fwupdate application 5.x.x
                                } else {
                                    //current version is 5.x.x
                                    if (verc == 1) {
                                        //There is higher available
                                        if (fwUpdate.hw[ite].compareTo("EXA5") == 0) {
                                            //But do not show bl_sd_app
                                            do_not_include = true;
                                        }
                                    }
                                }
                            }

                            if (do_not_include) break;
                        }
                    }


                    /* Only add to list if targets current device */
                    // TODO fix this when DFU hw is sorted out
                    //if(Arrays.asList(fwUpdate.hw).contains(hwType) && do_not_include==false) {
                    if(do_not_include == false) {
                        availableUpdates.add(fwUpdate);
                    }
                }
            }
        } catch (Exception e){
            Log.i("VER","ERROR=" + e.getMessage());
        }
        //Log.e("fetchAvailableUpdates size=", Integer.toString(availableUpdates.size()));
        return availableUpdates;
    }

    public List<UpdateContainer> fetchBldrUpdates(){
        return  fetchAvailableUpdates(false);
    }

    public List<UpdateContainer> fetchApplicationUpdates(){
        return  fetchAvailableUpdates(true);
    }

    public UpdateContainer fetchLastApplicationUpdate(){
        List<UpdateContainer> updatesList = fetchAvailableUpdates(true);
        return (updatesList.isEmpty()) ? null : updatesList.get(0);
    }

    public UpdateContainer fetchLastBldrUpdate(){
        List<UpdateContainer> updatesList = fetchAvailableUpdates(false);
        if(updatesList.size() == 0) return null;
        return updatesList.get(0);
        //return (updatesList.isEmpty()) ? null : updatesList.get(0);
    }

    public String grabUpdateFile(UpdateContainer update){
        try {
            return new ExecuteDownloadOperation().execute(update).get();
        } catch (Exception e){
            return null;
        }
    }

    abstract public boolean startUpdate();
    abstract public void abortUpdate();
    abstract public void pauseUpdate();
    abstract public void resumeUpdate();

    /**
     * Check for updates using current version
     * @return true if update is available false if not
     */
    public boolean isAppUpdateAvailable(){
        try {
            UpdateContainer last = fetchLastApplicationUpdate();
            if(checkVersion(appVersion,last.version)){
                mAppUpdateVersion = last.version;
                return true;
            }
        } catch (Exception e){
            // TODO
        }
        return  false;
    }

    /**
     * Check for updates using current version
     * @return true if update is available false if not
     */
    public boolean isBldrUpdateAvailable(){
        try {
            UpdateContainer last = fetchLastBldrUpdate();
            if(checkVersion(bldrVersion,last.version)){
                mBldrUpdateVersion = last.version;
                return true;
            }
        } catch (Exception e){

        }
        return  false;
    }

    /**
     * Compares remote and current versions
     * @param currentVersion
     * @param remoteVersion
     * @return true if remote is newer false if not
     */
    abstract public boolean checkVersion(String currentVersion, String remoteVersion);


}
