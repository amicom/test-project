//jDownloader - Downloadmanager
//Copyright (C) 2009  JD-Team support@jdownloader.org
//
//This program is free software: you can redistribute it and/or modify
//it under the terms of the GNU General Public License as published by
//the Free Software Foundation, either version 3 of the License, or
//(at your option) any later version.
//
//This program is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
//GNU General Public License for more details.
//
//You should have received a copy of the GNU General Public License
//along with this program.  If not, see <http://www.gnu.org/licenses/>.

package jd.plugins.decrypter;

import java.util.ArrayList;

import jd.PluginWrapper;
import jd.controlling.ProgressController;
import jd.nutils.encoding.Encoding;
import jd.plugins.CryptedLink;
import jd.plugins.DecrypterPlugin;
import jd.plugins.DownloadLink;
import jd.plugins.PluginForDecrypt;

@DecrypterPlugin(revision = "$Revision: 24071 $", interfaceVersion = 2, names = { "javmon.com" }, urls = { "http://(www\\.)?javmon\\.com/online\\-\\d+/video\\-\\d+/[a-z0-9\\-_]+\\.html" }, flags = { 0 })
public class JavMonCom extends PluginForDecrypt {

    public JavMonCom(PluginWrapper wrapper) {
        super(wrapper);
    }

    public ArrayList<DownloadLink> decryptIt(CryptedLink param, ProgressController progress) throws Exception {
        ArrayList<DownloadLink> decryptedLinks = new ArrayList<DownloadLink>();
        final String parameter = param.toString();
        br.getPage(parameter);
        String externID = br.getRegex("flashx\\.php\\?url=(http://flashx\\.tv/video/[^<>\"]*?)\"").getMatch(0);
        if (externID == null) {
            externID = br.getRegex("(http%3A%2F%2Fsexvidx\\.com%2Fembed%2F\\d+\\-sexvidx\\.com\\.html)").getMatch(0);
        }
        if (externID == null) {
            if (!br.containsHTML("s1\\.addParam\\(\\'flashvars\\'")) {
                logger.info("Link offline: " + parameter);
                return decryptedLinks;
            }
            logger.warning("Decrypter broken for link: " + parameter);
            return null;
        }
        externID = Encoding.htmlDecode(externID);
        decryptedLinks.add(createDownloadlink(externID));

        return decryptedLinks;
    }

}
