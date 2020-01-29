package com.box.l10n.mojito.service.thirdparty;

import com.box.l10n.mojito.entity.Asset;
import com.box.l10n.mojito.entity.AssetContent;
import com.box.l10n.mojito.entity.Repository;
import com.box.l10n.mojito.entity.Screenshot;
import com.box.l10n.mojito.entity.ScreenshotRun;
import com.box.l10n.mojito.entity.ScreenshotTextUnit;
import com.box.l10n.mojito.entity.TMTextUnit;
import com.box.l10n.mojito.entity.ThirdPartyScreenshot;
import com.box.l10n.mojito.service.asset.AssetRepository;
import com.box.l10n.mojito.service.asset.AssetService;
import com.box.l10n.mojito.service.assetExtraction.ServiceTestBase;
import com.box.l10n.mojito.service.image.ImageService;
import com.box.l10n.mojito.service.pollableTask.PollableFuture;
import com.box.l10n.mojito.service.pollableTask.PollableTaskException;
import com.box.l10n.mojito.service.pollableTask.PollableTaskService;
import com.box.l10n.mojito.service.repository.RepositoryNameAlreadyUsedException;
import com.box.l10n.mojito.service.repository.RepositoryService;
import com.box.l10n.mojito.service.screenshot.ScreenshotRepository;
import com.box.l10n.mojito.service.screenshot.ScreenshotService;
import com.box.l10n.mojito.service.tm.TMTextUnitRepository;
import com.box.l10n.mojito.smartling.AssetPathAndTextUnitNameKeys;
import com.box.l10n.mojito.smartling.SmartlingClient;
import com.box.l10n.mojito.test.TestIdWatcher;
import com.google.common.io.ByteStreams;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;

import java.io.IOException;
import java.util.List;
import java.util.regex.Pattern;

import static com.box.l10n.mojito.entity.Screenshot.Status.ACCEPTED;
import static org.junit.Assert.assertEquals;

/**
 * Integration test that needs Smartling account and verfication on the screenshot upload done manually in Smartling
 */
public class ThirdPartyTMSSmartlingTest extends ServiceTestBase {

    static Logger logger = LoggerFactory.getLogger(SmartlingClient.class);

    @Rule
    public TestIdWatcher testIdWatcher = new TestIdWatcher();

    @Autowired(required = false)
    SmartlingClient smartlingClient;

    @Autowired
    AssetPathAndTextUnitNameKeys assetPathAndTextUnitNameKeys;

    @Autowired
    ThirdPartyService thirdPartyService;

    @Autowired
    RepositoryService repositoryService;

    @Autowired
    AssetService assetService;

    @Autowired
    AssetRepository assetRepository;

    @Autowired
    PollableTaskService pollableTaskService;

    @Autowired
    ScreenshotService screenshotService;

    @Autowired
    TMTextUnitRepository tmTextUnitRepository;
    @Autowired
    ScreenshotRepository screenshotRepository;

    @Autowired
    ThirdPartyScreenshotRepository thirdPartyScreenshotRepository;

    @Autowired
    ImageService imageService;

    @Value("${test.l10n.smartling.project-id:#{null}}")
    String projectId = null;

    @Value("${test.l10n.smartling.file-uri:#{null}}")
    String fileUri = null;

    @Before
    public void init() {
        Assume.assumeNotNull(smartlingClient);
    }

    @Test
    public void testMappingAndScreenshot() throws Exception {

        ThirdPartyServiceTestData thirdPartyServiceTestData = new ThirdPartyServiceTestData(testIdWatcher);
        Repository repository = thirdPartyServiceTestData.repository;

        String smartlingContent = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<resources>\n" +
                "    <!--comment 1-->\n" +
                "    <string name=\"src/main/res/values/strings.xml#@#hello\" tmTextUnitId=\"946852\">Hello</string>\n" +
                "    <!-- twice the same name in the 3rd party tms shouldn't break the mapping -->\n" +
                "    <string name=\"src/main/res/values/strings.xml#@#hello\" tmTextUnitId=\"8464561\">Hello-samename</string>\n" +
                "    <!--comment 2-->\n" +
                "    <string name=\"src/main/res/values/strings.xml#@#bye\" tmTextUnitId=\"946853\">Bye</string>\n" +
                "    <plurals name=\"src/main/res/values/strings.xml#@#plural_things\">\n" +
                "        <item quantity=\"one\">One thing</item>\n" +
                "        <item quantity=\"other\">Multiple things</item>\n" +
                "    </plurals>" +
                "</resources>";

        String smartlingFileUri = repository.getName() + "/0000_singular_source.xml";
        smartlingClient.uploadFile(projectId, smartlingFileUri, "android", smartlingContent, null, null);

        logger.debug("First mapping");
        thirdPartyService.mapMojitoAndThirdPartyTextUnits(repository, projectId);

        logger.debug("Second mapping");
        thirdPartyService.mapMojitoAndThirdPartyTextUnits(repository, projectId);

        thirdPartyService.uploadScreenshotsAndCreateMappings(repository, projectId);
    }

    @Test
    public void testGetFilePattern() {
        ThirdPartyTMSSmartling thirdPartyTMSSmartling = new ThirdPartyTMSSmartling();
        Pattern webappmigration = thirdPartyTMSSmartling.getFilePattern("repositoryName");
        Assert.assertTrue(webappmigration.matcher("repositoryName/0000_singular_source.xml").matches());
        Assert.assertTrue(webappmigration.matcher("repositoryName/0000_plural_source.xml").matches());
        Assert.assertFalse(webappmigration.matcher("someotherrepo/0000_plural_source.xml").matches());
        Assert.assertFalse(webappmigration.matcher("something").matches());
    }

}