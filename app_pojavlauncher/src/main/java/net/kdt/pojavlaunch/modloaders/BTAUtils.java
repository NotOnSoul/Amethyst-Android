package net.kdt.pojavlaunch.modloaders;

import android.util.Log;

import androidx.annotation.Keep;

import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.utils.DownloadUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

public class BTAUtils {
    private static final String BTA_CLIENT_URL = "https://downloads.betterthanadventure.net/bta-client/%s/%s/client.jar";
    private static final String BTA_ICON_URL = "https://downloads.betterthanadventure.net/bta-client/%s/%s/auto/%s.png";
    private static final List<String> BTA_TESTED_VERSIONS = new ArrayList<>();
    static {
        BTA_TESTED_VERSIONS.add("v7.3");
        BTA_TESTED_VERSIONS.add("v7.2_01");
        BTA_TESTED_VERSIONS.add("v7.2");
        BTA_TESTED_VERSIONS.add("v7.1_01");
        BTA_TESTED_VERSIONS.add("v7.1");
    }

    private static String getIconUrl(String version, String buildType) {
        String iconName = version.replace('.','_');
        if(buildType.equals("nightly")) iconName = "v"+iconName;
        return String.format(BTA_ICON_URL, buildType, version, iconName);
    }

    private static List<BTAVersion> createVersionList(List<String> versionStrings, String buildType) {
        ListIterator<String> iterator = versionStrings.listIterator(versionStrings.size());
        ArrayList<BTAVersion> btaVersions = new ArrayList<>(versionStrings.size());
        while(iterator.hasPrevious()) {
            String version = iterator.previous();
            if(version == null) continue;
            btaVersions.add(new BTAVersion(
                    version,
                    String.format(BTA_CLIENT_URL, buildType, version),
                    getIconUrl(version, buildType)
            ));
        }
        btaVersions.trimToSize();
        return btaVersions;
    }

    private static List<BTAVersion> processNightliesJson(String nightliesInfo) throws JsonParseException {
        BTAVersionsManifest manifest = Tools.GLOBAL_GSON.fromJson(nightliesInfo, BTAVersionsManifest.class);
        return createVersionList(manifest.versions, "nightly");
    }

    private static BTAVersionList processReleasesJson(String releasesInfo) throws JsonParseException {
        BTAVersionsManifest manifest = Tools.GLOBAL_GSON.fromJson(releasesInfo, BTAVersionsManifest.class);
        List<String> stringVersions = manifest.versions;
        List<String> testedVersions = new ArrayList<>();
        List<String> untestedVersions = new ArrayList<>();
        for(String version : stringVersions) {
            if(version == null) break;
            if(BTA_TESTED_VERSIONS.contains(version)) {
                testedVersions.add(version);
            }else {
                untestedVersions.add(version);
            }
        }

        return new BTAVersionList(
                createVersionList(testedVersions, "release"),
                createVersionList(untestedVersions, "release"),
                null
        );
    }

    public static BTAVersionList downloadVersionList() throws IOException {
        try {
            BTAVersionList releases = DownloadUtils.downloadStringCached(
                    "https://downloads.betterthanadventure.net/bta-client/release/versions.json",
                    "bta_releases", BTAUtils::processReleasesJson);
            List<BTAVersion> nightlies = DownloadUtils.downloadStringCached(
                    "https://downloads.betterthanadventure.net/bta-client/nightly/versions.json",
                    "bta_nightlies", BTAUtils::processNightliesJson);
            return new BTAVersionList(releases.testedVersions, releases.untestedVersions, nightlies);
        }catch (DownloadUtils.ParseException e) {
            Log.e("BTAUtils", "Failed to process json", e);
            return null;
        }
    }

    private static class BTAVersionsManifest {
        @Keep
        public List<String> versions;
        @Keep
        @SerializedName("default")
        public String defaultVersion;
    }

    public static class BTAVersion {
        public final String versionName;
        public final String downloadUrl;
        public final String iconUrl;

        public BTAVersion(String versionName, String downloadUrl, String iconUrl) {
            this.versionName = versionName;
            this.downloadUrl = downloadUrl;
            this.iconUrl = iconUrl;
        }
    }
    public static class BTAVersionList {
        public final List<BTAVersion> testedVersions;
        public final List<BTAVersion> untestedVersions;
        public final List<BTAVersion> nightlyVersions;

        public BTAVersionList(List<BTAVersion> mTestedVersions, List<BTAVersion> mUntestedVersions, List<BTAVersion> nightlyVersions) {
            this.testedVersions = mTestedVersions;
            this.untestedVersions = mUntestedVersions;
            this.nightlyVersions = nightlyVersions;
        }
    }
}
