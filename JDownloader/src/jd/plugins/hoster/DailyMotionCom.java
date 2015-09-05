//    jDownloader - Downloadmanager
//    Copyright (C) 2009  JD-Team support@jdownloader.org
//
//    This program is free software: you can redistribute it and/or modify
//    it under the terms of the GNU General Public License as published by
//    the Free Software Foundation, either version 3 of the License, or
//    (at your option) any later version.
//
//    This program is distributed in the hope that it will be useful,
//    but WITHOUT ANY WARRANTY; without even the implied warranty of
//    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
//    GNU General Public License for more details.
//
//    You should have received a copy of the GNU General Public License
//    along with this program.  If not, see <http://www.gnu.org/licenses/>.

package jd.plugins.hoster;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import jd.PluginWrapper;
import jd.config.ConfigContainer;
import jd.config.ConfigEntry;
import jd.config.SubConfiguration;
import jd.http.Browser;
import jd.http.URLConnectionAdapter;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.plugins.Account;
import jd.plugins.AccountInfo;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForHost;
import jd.plugins.download.DownloadInterface;
import jd.utils.JDUtilities;
import jd.utils.locale.JDL;

@HostPlugin(revision = "$Revision: 31334 $", interfaceVersion = 2, names = { "dailymotion.com" }, urls = { "http://dailymotiondecrypted\\.com/video/\\w+" }, flags = { 2 })
public class DailyMotionCom extends PluginForHost {
    private static String getQuality(final String quality, final String videosource) {
        return new Regex(videosource, "\"" + quality + "\":\"(http[^<>\"\\']+)\"").getMatch(0);
    }

    /* Sync the following functions in hoster- and decrypterplugin */

    private static AtomicBoolean hostplg_loaded = new AtomicBoolean(false);

    public static String getVideosource(final Browser br) {
        if (!hostplg_loaded.getAndSet(true)) {
            JDUtilities.getPluginForDecrypt("dailymotion.com");
        }
        return jd.plugins.decrypter.DailyMotionComDecrypter.getVideosource(br);
    }

    public static LinkedHashMap<String, String[]> findVideoQualities(final Browser br, final String parameter, String videosource) throws IOException {
        if (!hostplg_loaded.getAndSet(true)) {
            JDUtilities.getPluginForDecrypt("dailymotion.com");
        }
        return jd.plugins.decrypter.DailyMotionComDecrypter.findVideoQualities(br, parameter, videosource);
    }

    public String                dllink                 = null;
    private static final String  MAINPAGE               = "http://www.dailymotion.com/";
    private static final String  REGISTEREDONLYUSERTEXT = "Download only possible for registered users";
    private static final String  COUNTRYBLOCKUSERTEXT   = "This video is not available for your country";
    /** Settings stuff */
    private static final String  ALLOW_BEST             = "ALLOW_BEST";
    private static final String  ALLOW_LQ               = "ALLOW_LQ";
    private static final String  ALLOW_SD               = "ALLOW_SD";
    private static final String  ALLOW_HQ               = "ALLOW_HQ";
    private static final String  ALLOW_720              = "ALLOW_720";
    private static final String  ALLOW_1080             = "ALLOW_1080";
    private static final String  ALLOW_OTHERS           = "ALLOW_OTHERS";
    private static final String  ALLOW_AUDIO            = "ALLOW_AUDIO";
    private static final String  ALLOW_HDS              = "ALLOW_HDS";
    private static final String  CUSTOM_DATE            = "CUSTOM_DATE";
    private static final String  CUSTOM_FILENAME        = "CUSTOM_FILENAME";

    private final static String  defaultCustomFilename  = "*videoname*_*quality**ext*";
    private final static String  defaultCustomDate      = "dd.MM.yyyy";
    private final static boolean defaultAllowAudio      = true;

    public DailyMotionCom(PluginWrapper wrapper) {
        super(wrapper);
        this.enablePremium("http://www.dailymotion.com/register");
        setConfigElements();
    }

    public void correctDownloadLink(final DownloadLink link) {
        link.setUrlDownload(link.getDownloadURL().replace("dailymotiondecrypted.com/", "dailymotion.com/"));
    }

    @Override
    public AvailableStatus requestFileInformation(final DownloadLink downloadLink) throws IOException, PluginException, ParseException {
        br.setFollowRedirects(true);
        br.setCookie("http://www.dailymotion.com", "family_filter", "off");
        br.setCookie("http://www.dailymotion.com", "ff", "off");
        br.setCookie("http://www.dailymotion.com", "lang", "en_US");
        prepBrowser();
        if (downloadLink.getBooleanProperty("offline", false)) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        } else if (downloadLink.getBooleanProperty("countryblock", false)) {
            downloadLink.getLinkStatus().setStatusText(JDL.L("plugins.hoster.dailymotioncom.countryblocked", COUNTRYBLOCKUSERTEXT));
            return AvailableStatus.TRUE;
        } else if (downloadLink.getBooleanProperty("registeredonly", false)) {
            downloadLink.getLinkStatus().setStatusText(JDL.L("plugins.hoster.dailymotioncom.registeredonly", REGISTEREDONLYUSERTEXT));
            return AvailableStatus.TRUE;
        }
        if (isHDS(downloadLink)) {

            // br.getPage(downloadLink.getStringProperty("directlink", null));
            downloadLink.getLinkStatus().setStatusText("HDS stream download is not supported (yet)!");
            downloadLink.setFinalFileName(getFormattedFilename(downloadLink));
            return AvailableStatus.FALSE;
        } else if (downloadLink.getBooleanProperty("isrtmp", false)) {
            getRTMPlink();
        } else if (isSubtitle(downloadLink)) {
            dllink = downloadLink.getStringProperty("directlink", null);

            if (!checkDirectLink(downloadLink) || dllink == null) {
                throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
            }

        } else {
            String mainlink = downloadLink.getStringProperty("mainlink", null);
            logger.info("mainlink: " + mainlink);
            dllink = downloadLink.getStringProperty("directlink", null);
            logger.info("dllink: " + dllink);
            if (dllink == null) {
                System.out.println(1);
            } else {
                System.out.println("DLink FOund");
            }

            /* .m4a links have wrong internal directlinks --> Size check not possible */
            if (!downloadLink.getName().contains(".m4a")) {
                if (!checkDirectLink(downloadLink) || dllink == null) {
                    dllink = findFreshDirectlink(downloadLink);
                    if (dllink == null) {
                        logger.warning("dllink is null...");
                        throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                    }
                    if (!checkDirectLink(downloadLink)) {
                        throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
                    }
                }
            }

        }
        downloadLink.setFinalFileName(getFormattedFilename(downloadLink));
        return AvailableStatus.TRUE;
    }

    public void doFree(final DownloadLink downloadLink) throws Exception {
        if (isHDS(downloadLink)) {
            throw new PluginException(LinkStatus.ERROR_FATAL, "HDS stream download is not supported (yet)!");
        } else if (dllink.startsWith("rtmp")) {
            downloadLink.setFinalFileName(getFormattedFilename(downloadLink));
            String[] stream = dllink.split("@");
            dl = new RTMPDownload(this, downloadLink, stream[0]);
            setupRTMPConnection(stream, dl);
            ((RTMPDownload) dl).startDownload();
        } else {
            downloadDirect(downloadLink);
        }
    }

    protected void downloadDirect(DownloadLink downloadLink) throws Exception {

        /* Workaround for old downloadcore bug that can lead to incomplete files */
        br.getHeaders().put("Accept-Encoding", "identity");
        downloadLink.setFinalFileName(getFormattedFilename(downloadLink));
        /*
         * They do allow resume and unlimited chunks but resuming or using more than 1 chunk causes problems, the file will then be
         * corrupted!
         */
        dl = jd.plugins.BrowserAdapter.openDownload(br, downloadLink, dllink, false, 1);
        /* Their servers usually return a valid size - if not, it's probably a server error */
        final long contentlength = dl.getConnection().getLongContentLength();
        if (contentlength == -1) {
            throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error", 30 * 60 * 1000l);
        }
        if (dl.getConnection().getContentType().contains("html")) {
            br.followConnection();
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        dl.startDownload();
    }

    private String findFreshDirectlink(final DownloadLink dl) throws IOException {
        try {
            dllink = null;
            /* Remove old cookies/Referer */
            this.br = new Browser();
            final String mainlink = dl.getStringProperty("mainlink", null);

            br.setFollowRedirects(true);
            br.getPage(mainlink);
            logger.info("findFreshDirectlink - getPage mainlink has been done, next: getVideosource");
            br.setFollowRedirects(false);
            final String videosource = getVideosource(br);
            if (videosource == null) {
                logger.info("videosource: " + videosource);
                return null;
            }
            LinkedHashMap<String, String[]> foundqualities = findVideoQualities(this.br, mainlink, videosource);
            final String qualityvalue = dl.getStringProperty("qualityvalue", null);
            final String directlinkinfo[] = foundqualities.get(qualityvalue);
            dllink = Encoding.htmlDecode(directlinkinfo[0]);
            onNewDirectLink(dl, dllink);
        } catch (final Throwable e) {
            dllink = null;
            return null;
        }
        return dllink;
    }

    protected void onNewDirectLink(DownloadLink dl, String dllink2) {
    }

    @Override
    public AccountInfo fetchAccountInfo(Account account) throws Exception {
        AccountInfo ai = new AccountInfo();
        try {
            login(account, this.br);
        } catch (PluginException e) {
            account.setValid(false);
            return ai;
        }
        ai.setUnlimitedTraffic();
        ai.setStatus("Registered (free) User");
        return ai;
    }

    protected boolean checkDirectLink(final DownloadLink downloadLink) throws PluginException {
        if (dllink != null) {
            br.setFollowRedirects(false);
            try {
                URLConnectionAdapter con = null;
                try {
                    if (isJDStable()) {
                        con = br.openGetConnection(dllink);
                    } else {
                        con = br.openHeadConnection(dllink);
                    }
                    if (con.getResponseCode() == 302) {
                        br.followConnection();
                        dllink = br.getRedirectLocation().replace("#cell=core&comment=", "");
                        br.getHeaders().put("Referer", dllink);
                        if (isJDStable()) {
                            con = br.openGetConnection(dllink);
                        } else {
                            con = br.openHeadConnection(dllink);
                        }
                    }
                    if (con.getResponseCode() == 410 || con.getContentType().contains("html")) {
                        return false;
                    }
                    downloadLink.setDownloadSize(con.getLongContentLength());
                } finally {
                    try {
                        con.disconnect();
                    } catch (Throwable e) {
                    }
                }
            } catch (final Exception e) {
                return false;
            }
            return true;
        }
        return false;
    }

    @Override
    public String getAGBLink() {
        return "http://www.dailymotion.com/de/legal/terms";

    }

    @Override
    public int getMaxSimultanFreeDownloadNum() {
        return -1;
    }

    @Override
    public int getMaxSimultanPremiumDownloadNum() {
        return -1;
    }

    @Override
    public void handleFree(DownloadLink downloadLink) throws Exception {
        requestFileInformation(downloadLink);
        if (downloadLink.getBooleanProperty("countryblock", false)) {
            throw new PluginException(LinkStatus.ERROR_FATAL, COUNTRYBLOCKUSERTEXT);
        }
        if (downloadLink.getBooleanProperty("registeredonly", false)) {
            throw new PluginException(LinkStatus.ERROR_FATAL, REGISTEREDONLYUSERTEXT);
        }
        doFree(downloadLink);
    }

    @Override
    public void handlePremium(final DownloadLink link, final Account account) throws Exception {
        login(account, this.br);
        requestFileInformation(link);
        if (link.getBooleanProperty("ishds", false)) {
            throw new PluginException(LinkStatus.ERROR_FATAL, "HDS stream download is not supported (yet)!");
        } else if (link.getBooleanProperty("countryblock", false)) {
            throw new PluginException(LinkStatus.ERROR_FATAL, COUNTRYBLOCKUSERTEXT);
        }
        doFree(link);
    }

    public void login(final Account account, final Browser br) throws Exception {
        this.setBrowserExclusive();
        br.setFollowRedirects(true);
        br.getHeaders().put("X-Requested-With", "XMLHttpRequest");
        br.getHeaders().put("X-Prototype-Version", "1.6.1");
        br.postPage("http://www.dailymotion.com/signin", "form_name=dm_pageitem_login&username=" + Encoding.urlEncode(account.getUser()) + "&password=" + Encoding.urlEncode(account.getPass()) + "&login_submit=Login");
        if (br.getCookie(MAINPAGE, "sid") == null || br.getCookie(MAINPAGE, "sdx") == null) {
            throw new PluginException(LinkStatus.ERROR_PREMIUM, PluginException.VALUE_ID_PREMIUM_DISABLE);
        }
    }

    private boolean isSubtitle(final DownloadLink dl) {
        return dl.getBooleanProperty("type_subtitle", false);
    }

    private void getRTMPlink() throws IOException, PluginException {
        final String[] values = br.getRegex("new SWFObject\\(\"(http://player\\.grabnetworks\\.com/swf/GrabOSMFPlayer\\.swf)\\?id=\\d+\\&content=v([0-9a-f]+)\"").getRow(0);
        if (values == null || values.length != 2) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        final Browser rtmp = br.cloneBrowser();
        rtmp.getPage("http://content.grabnetworks.com/v/" + values[1] + "?from=" + dllink);
        dllink = rtmp.getRegex("\"url\":\"(rtmp[^\"]+)").getMatch(0);
        if (dllink == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        dllink = dllink + "@" + values[0];
    }

    private void setupRTMPConnection(String[] stream, DownloadInterface dl) {
        jd.network.rtmp.url.RtmpUrlConnection rtmp = ((RTMPDownload) dl).getRtmpConnection();
        rtmp.setUrl(stream[0]);
        rtmp.setSwfVfy(stream[1]);
        rtmp.setResume(true);
    }

    private void prepBrowser() {
        br.getHeaders().put("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64; rv:39.0) Gecko/20100101 Firefox/39.0");
        br.getHeaders().put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        br.getHeaders().put("Accept-Language", "de, en-gb;q=0.9, en;q=0.8");
        br.getHeaders().put("Accept-Encoding", "gzip");
        br.getHeaders().put("Accept-Charset", "ISO-8859-1,utf-8;q=0.7,*;q=0.7");
    }

    private boolean isHDS(final DownloadLink dl) {
        return "hds".equals(dl.getStringProperty("qualityname", null));
    }

    /* NO OVERRIDE!! We need to stay 0.9*compatible */
    public boolean allowHandle(final DownloadLink downloadLink, final PluginForHost plugin) {
        return downloadLink.getHost().equalsIgnoreCase(plugin.getHost());
    }

    @Override
    public String getDescription() {
        return "JDownloader's DailyMotion plugin helps downloading Videoclips from dailymotion.com. DailyMotion provides different video formats and qualities.";
    }

    final static String[][] REPLACES = { { "plain_date", "date", "Date when the video was uploaded" }, { "plain_videoid", "videoid", "ID of the video" }, { "plain_channel", "channelname", "The name of the channel/uploader" }, { "plain_ext", "ext", "Extension of the file (usually .mp4)" }, { "qualityname", "quality", "Quality of the video" }, { "plain_videoname", "videoname", "Name of the video" } };

    public static String getFormattedFilename(final DownloadLink downloadLink) throws ParseException {
        final SubConfiguration cfg = SubConfiguration.getConfig("dailymotion.com");
        String formattedFilename = cfg.getStringProperty(CUSTOM_FILENAME, defaultCustomFilename);
        if (!formattedFilename.contains("*videoname") && !formattedFilename.contains("*ext*") && !formattedFilename.contains("*videoid*") && !formattedFilename.contains("*channelname*")) {
            formattedFilename = defaultCustomFilename;
        }
        for (final String[] replaceinfo : REPLACES) {
            final String property = replaceinfo[0];
            final String fulltagname = "*" + replaceinfo[1] + "*";
            String tag_data = downloadLink.getStringProperty(property, "-");
            if (fulltagname.equals("*date*")) {
                if (tag_data.equals("-")) {
                    tag_data = "0";
                }
                final String userDefinedDateFormat = cfg.getStringProperty(CUSTOM_DATE, defaultCustomDate);
                SimpleDateFormat formatter = null;

                Date theDate = new Date(Long.parseLong(tag_data));

                if (userDefinedDateFormat != null) {
                    try {
                        formatter = new SimpleDateFormat(userDefinedDateFormat);
                        tag_data = formatter.format(theDate);
                    } catch (Exception e) {
                        // prevent user error killing plugin.
                        tag_data = "-";
                    }
                }
            }
            formattedFilename = formattedFilename.replace(fulltagname, tag_data);
        }
        formattedFilename = correctFilename(formattedFilename);
        return formattedFilename;
    }

    public static String correctFilename(String filename) {
        // Cut filenames if they're too long
        if (filename.length() > 240) {
            final String ext = filename.substring(filename.lastIndexOf("."));
            int extLength = ext.length();
            filename = filename.substring(0, 240 - extLength);
            filename += ext;
        }
        return filename;
    }

    private boolean isJDStable() {
        return System.getProperty("jd.revision.jdownloaderrevision") == null;
    }

    private void setConfigElements() {
        final ConfigEntry hq = addConfigElementBestOnly();
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_SEPARATOR));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, getPluginConfig(), ALLOW_LQ, JDL.L("plugins.hoster.dailymotioncom.checkLQ", "Grab LQ/LD [320x240]?")).setDefaultValue(true).setEnabledCondidtion(hq, false));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, getPluginConfig(), ALLOW_SD, JDL.L("plugins.hoster.dailymotioncom.checkSD", "Grab SD/HQ [512x384]?")).setDefaultValue(true).setEnabledCondidtion(hq, false));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, getPluginConfig(), ALLOW_HQ, JDL.L("plugins.hoster.dailymotioncom.checkHQ", "Grab HQ/HD [848x480]?")).setDefaultValue(true).setEnabledCondidtion(hq, false));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, getPluginConfig(), ALLOW_720, JDL.L("plugins.hoster.dailymotioncom.check720", "Grab [1280x720]?")).setDefaultValue(true).setEnabledCondidtion(hq, false));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, getPluginConfig(), ALLOW_1080, JDL.L("plugins.hoster.dailymotioncom.check1080", "Grab [1920x1080]?")).setDefaultValue(true).setEnabledCondidtion(hq, false));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, getPluginConfig(), ALLOW_AUDIO, JDL.L("plugins.hoster.dailymotioncom.checkaudio", "Allow audio download")).setDefaultValue(defaultAllowAudio));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, getPluginConfig(), ALLOW_OTHERS, JDL.L("plugins.hoster.dailymotioncom.checkother", "Grab other available qualities (RTMP/OTHERS)?")).setDefaultValue(true).setEnabledCondidtion(hq, false));
        addConfigElementHDS(hq);
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_SEPARATOR));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_LABEL, "Customize the filenames"));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_TEXTFIELD, getPluginConfig(), CUSTOM_DATE, JDL.L("plugins.hoster.dailymotioncom.customdate", "Define how the date should look.")).setDefaultValue(defaultCustomDate));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_SEPARATOR));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_LABEL, "Customize the filename! Example: '*channelname*_*date*_*videoname**ext*'"));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_TEXTFIELD, getPluginConfig(), CUSTOM_FILENAME, JDL.L("plugins.hoster.dailymotioncom.customfilename", "Define how the filenames should look:")).setDefaultValue(defaultCustomFilename));
        final StringBuilder sb = new StringBuilder();
        sb.append("<html>");
        sb.append("Explanation of the available tags:<br>");
        for (final String[] replaceinfo : REPLACES) {
            final String fulltagname = "*" + replaceinfo[1] + "*";
            final String tagdescription = replaceinfo[2];
            sb.append(fulltagname + " = " + tagdescription + "<br>");
        }
        sb.append("</html>");
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_LABEL, sb.toString()));
    }

    public void addConfigElementHDS(final ConfigEntry hq) {
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, getPluginConfig(), ALLOW_HDS, JDL.L("plugins.hoster.dailymotioncom.checkhds", "Grab hds (not downloadable yet!)?")).setDefaultValue(false).setEnabledCondidtion(hq, false));
    }

    public ConfigEntry addConfigElementBestOnly() {
        final ConfigEntry hq = new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, getPluginConfig(), ALLOW_BEST, JDL.L("plugins.hoster.dailymotioncom.checkbest", "Only grab the best available resolution")).setDefaultValue(false);
        getConfig().addEntry(hq);
        return hq;
    }

    @Override
    public void reset() {
    }

    @Override
    public void resetDownloadlink(DownloadLink link) {
    }

    @Override
    public void resetPluginGlobals() {
    }

}