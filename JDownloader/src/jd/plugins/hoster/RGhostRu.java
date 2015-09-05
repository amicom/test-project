//    jDownloader - Downloadmanager
//    Copyright (C) 2012  JD-Team support@jdownloader.org
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

import jd.PluginWrapper;
import jd.http.Browser.BrowserException;
import jd.parser.html.Form;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.Plugin;
import jd.plugins.PluginException;
import jd.plugins.PluginForHost;

import org.appwork.utils.formatter.SizeFormatter;

//rghost.ru by pspzockerscene
@HostPlugin(revision = "$Revision: 29680 $", interfaceVersion = 2, names = { "rghost.ru" }, urls = { "http://([a-z0-9]+\\.)?rghost\\.(?:net|ru)/.+" }, flags = { 0 })
public class RGhostRu extends PluginForHost {

    private static final String PWTEXT              = "id=\"password_field\"";

    private static final String type_private_all    = "http://([a-z0-9]+\\.)?rghost\\.net/private/.+";
    private static final String type_private_direct = "http://([a-z0-9]+\\.)?rghost\\.net/private/[A-Za-z0-9]+/[a-f0-9]{32}/.+";
    private static final String type_normal_direct  = "http://([a-z0-9]+\\.)?rghost\\.net/[A-Za-z0-9]+/.+";

    public RGhostRu(PluginWrapper wrapper) {
        super(wrapper);
        // this host blocks if there is no timegap between the simultan
        // downloads so waittime is 3,5 sec right now, works good!
        this.setStartIntervall(3500l);
    }

    @Override
    public String getAGBLink() {
        return "http://rghost.ru/tos";
    }

    @Override
    public int getMaxSimultanFreeDownloadNum() {
        return -1;
    }

    @SuppressWarnings("deprecation")
    public void correctDownloadLink(DownloadLink link) {
        String newlink = link.getDownloadURL().replaceAll("((tr|pl)\\.)?rghost\\.(?:net|ru)/", "rghost.net/");
        if (newlink.matches(type_private_direct) || (newlink.matches(type_normal_direct) && !newlink.matches(type_private_all))) {
            /* Directlinks --> Change to normal links */
            newlink = newlink.substring(0, newlink.lastIndexOf("/"));
        } else {
            /* Picture view-links */
            if (newlink.endsWith(".view")) {
                newlink = newlink.substring(0, newlink.lastIndexOf("."));
            }
        }
        link.setUrlDownload(newlink);
    }

    @Override
    public void handleFree(final DownloadLink link) throws Exception {
        requestFileInformation(link);
        if (br.getHttpConnection().getResponseCode() == 503) {
            throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server maintenance");
        }
        br.setFollowRedirects(false);
        String dllink = getDownloadlink();
        String passCode = null;
        if (dllink == null && br.containsHTML(PWTEXT)) {
            Form pwform = br.getFormbyKey("password");
            if (pwform == null) {
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }

            if (link.getStringProperty("pass", null) == null) {
                passCode = Plugin.getUserInput("Password?", link);
            } else {
                /* gespeicherten PassCode holen */
                passCode = link.getStringProperty("pass", null);
            }
            pwform.put("password", passCode);
            /* Correct some stuff */
            pwform.remove("commit");
            pwform.put("commit", "Get+link");
            pwform.put("utf8", "%E2%9C%93");
            br.getHeaders().put("X-Requested-With", "XMLHttpRequest");
            br.getHeaders().put("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
            br.getHeaders().put("Accept", "*/*;q=0.5, text/javascript, application/javascript, application/ecmascript, application/x-ecmascript");
            // br.getHeaders().put("X-CSRF-Token", "");
            br.submitForm(pwform);
            /* Correct html */
            br.getRequest().setHtmlCode(br.toString().replace("\\", ""));
            dllink = getDownloadlink();
            if (dllink == null && br.containsHTML(PWTEXT)) {
                link.setProperty("pass", null);
                logger.info("DownloadPW wrong!");
                throw new PluginException(LinkStatus.ERROR_RETRY);
            }
        }
        if (dllink == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        dl = jd.plugins.BrowserAdapter.openDownload(br, link, dllink, true, 0);
        if (!dl.getConnection().isContentDisposition()) {
            br.followConnection();
            if (br.containsHTML(">409</div>")) {
                sleep(20000l, link);
                throw new PluginException(LinkStatus.ERROR_RETRY);
            }
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        if (passCode != null) {
            link.setProperty("pass", passCode);
        }
        dl.startDownload();
    }

    private String getDownloadlink() {
        String dllink = br.getRegex("\"(http://rghost\\.net/download/[^<>\"]*?)\"").getMatch(0);
        return dllink;
    }

    private void offlineCheck() throws PluginException {
        if (br.containsHTML("(Access to the file (is|was) restricted|the action is prohibited, this is a private file and your key is incorrect|<title>404|File was deleted|>File is deleted<)")) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public AvailableStatus requestFileInformation(final DownloadLink link) throws IOException, PluginException {
        this.setBrowserExclusive();
        br.setFollowRedirects(true);
        try {
            br.getPage(link.getDownloadURL());
        } catch (final BrowserException e) {
            if (br.getHttpConnection().getResponseCode() == 503) {
                link.getLinkStatus().setStatusText("Server maintenance");
                return AvailableStatus.UNCHECKABLE;
            }
            throw e;
        }
        // error clause for offline links
        if (br.containsHTML(">[\r\n]+File is deleted\\.[\r\n]+<") || !br.containsHTML("id=\"actions\"")) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        String filename = br.getRegex("rel=\"alternate\" title=\"Comments for the file ([^<>\"]*?)\"").getMatch(0);
        if (filename == null) {
            filename = br.getRegex("title=\"Comments for the file (.*?)\"").getMatch(0);
        }
        String filesize = br.getRegex("<small>([\r\n]+)?\\((.*?)\\)([\r\n]+)?</small>").getMatch(1);
        if (filesize == null) {
            filesize = br.getRegex("class=\"filesize\">\\((.*?)\\)</span>").getMatch(0);
        }
        // will pick up the first filesize mentioned.. as last resort fail over.
        if (filesize == null) {
            filesize = br.getRegex("(?i)([\\d\\.]+ ?(KB|MB|GB))").getMatch(0);
        }
        if (filename == null) {
            offlineCheck();
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        String md5 = br.getRegex("<b>MD5</b></td><td>(.*?)</td></tr>").getMatch(0);
        if (md5 == null) {
            md5 = br.getRegex("(?i)MD5((<[^>]+>)+?([\r\n\t ]+)?)+?([a-z0-9]{32})").getMatch(3);
        }
        if (md5 != null) {
            link.setMD5Hash(md5.trim());
        }
        String sha1 = br.getRegex("<b>SHA1</b></td><td>(.*?)</td></tr>").getMatch(0);
        if (sha1 == null) {
            sha1 = br.getRegex("(?i)SHA1((<[^>]+>)+?([\r\n\t ]+)?)+?([a-z0-9]{40})").getMatch(3);
        }
        if (sha1 != null) {
            link.setSha1Hash(sha1.trim());
        }
        link.setName(filename);
        link.setDownloadSize(SizeFormatter.getSize(filesize));
        offlineCheck();
        if (br.containsHTML(PWTEXT)) {
            link.getLinkStatus().setStatusText("This file is password protected");
        }
        return AvailableStatus.TRUE;
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