/*
 * Copyright (C) 2020-2024 Michael Clarke
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 *
 */
package com.github.mc1arke.sonarqube.plugin.ce.pullrequest.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.URL;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kohsuke.github.GHCheckRun;
import org.kohsuke.github.GHCheckRunBuilder;
import org.kohsuke.github.GHIssueComment;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHUser;
import org.kohsuke.github.GitHub;
import org.mockito.ArgumentCaptor;
import org.mockito.invocation.InvocationOnMock;
import org.sonar.api.ce.posttask.QualityGate;
import org.sonar.api.issue.impact.Severity;
import org.sonar.api.issue.impact.SoftwareQuality;
import org.sonar.ce.task.projectanalysis.component.Component;
import org.sonar.db.alm.setting.ALM;
import org.sonar.db.alm.setting.AlmSettingDto;
import org.sonar.db.alm.setting.ProjectAlmSettingDto;

import com.github.mc1arke.sonarqube.plugin.almclient.github.GithubClientFactory;
import com.github.mc1arke.sonarqube.plugin.ce.pullrequest.AnalysisDetails;
import com.github.mc1arke.sonarqube.plugin.ce.pullrequest.DecorationResult;
import com.github.mc1arke.sonarqube.plugin.ce.pullrequest.PostAnalysisIssueVisitor;
import com.github.mc1arke.sonarqube.plugin.ce.pullrequest.markup.Document;
import com.github.mc1arke.sonarqube.plugin.ce.pullrequest.markup.Formatter;
import com.github.mc1arke.sonarqube.plugin.ce.pullrequest.markup.MarkdownFormatterFactory;
import com.github.mc1arke.sonarqube.plugin.ce.pullrequest.report.AnalysisSummary;
import com.github.mc1arke.sonarqube.plugin.ce.pullrequest.report.ReportGenerator;

class GithubPullRequestDecoratorTest {

    private final AnalysisDetails analysisDetails = mock();
    private final GithubClientFactory githubClientFactory = mock();
    private final ReportGenerator reportGenerator = mock();
    private final MarkdownFormatterFactory markdownFormatterFactory = mock();
    private final Clock clock = Clock.fixed(Instant.ofEpochSecond(102030405), ZoneId.of("UTC"));
    private final GithubPullRequestDecorator testCase = new GithubPullRequestDecorator(githubClientFactory, reportGenerator, markdownFormatterFactory, clock);
    private final ProjectAlmSettingDto projectAlmSettingDto = mock();
    private final AlmSettingDto almSettingDto = mock();
    private final AnalysisSummary analysisSummary = mock();
    private final GitHub gitHub = mock();

    @BeforeEach
    void setUp() throws IOException {
        when(projectAlmSettingDto.getAlmRepo()).thenReturn("alm-repo");
        when(projectAlmSettingDto.getMonorepo()).thenReturn(false);
        when(analysisDetails.getPullRequestId()).thenReturn("123");
        when(analysisDetails.getAnalysisDate()).thenReturn(Date.from(clock.instant()));
        when(analysisDetails.getAnalysisId()).thenReturn("analysis-id");
        when(analysisDetails.getAnalysisProjectKey()).thenReturn("project-key");
        when(analysisDetails.getAnalysisProjectName()).thenReturn("Project Name");
        when(analysisDetails.getQualityGateStatus()).thenReturn(QualityGate.Status.OK);
        when(analysisDetails.getCommitSha()).thenReturn("commit-sha");
        List<PostAnalysisIssueVisitor.ComponentIssue> reportableIssues = IntStream.range(0, 20).mapToObj(i -> {
            PostAnalysisIssueVisitor.ComponentIssue componentIssue = mock();
            Component component = mock();
            when(componentIssue.getScmPath()).thenReturn(Optional.of("path" + i));
            when(componentIssue.getComponent()).thenReturn(component);
            PostAnalysisIssueVisitor.LightIssue lightIssue = mock();
            when(lightIssue.getMessage()).thenReturn("issue message " + i);
            when(lightIssue.getLine()).thenReturn(i);
            when(lightIssue.impacts()).thenReturn(Map.of(SoftwareQuality.values()[i % SoftwareQuality.values().length], Severity.values()[i % Severity.values().length]));
            when(componentIssue.getIssue()).thenReturn(lightIssue);
            return componentIssue;
        }).collect(Collectors.toList());
        when(analysisDetails.getScmReportableIssues()).thenReturn(reportableIssues);

        when(reportGenerator.createAnalysisSummary(any())).thenReturn(analysisSummary);
        when(analysisSummary.getDashboardUrl()).thenReturn("dashboard-url");
        when(analysisSummary.format(any())).thenReturn("report summary");
        when(githubClientFactory.createClient(any(), any())).thenReturn(gitHub);
    }

    @Test
    void shouldReturnCorrectAlms() {
        assertThat(testCase.alm()).isEqualTo(List.of(ALM.GITHUB));
    }

    @Test
    void shouldThrowExceptionIfClientCreationFails() throws IOException {
        Exception dummyException = new IOException("Dummy Exception");
        when(githubClientFactory.createClient(any(), any())).thenThrow(dummyException);

        assertThatThrownBy(() -> testCase.decorateQualityGateStatus(analysisDetails, almSettingDto, projectAlmSettingDto))
                .hasMessage("Could not decorate Pull Request on Github")
                .isExactlyInstanceOf(IllegalStateException.class).hasCause(dummyException);
    }
}
