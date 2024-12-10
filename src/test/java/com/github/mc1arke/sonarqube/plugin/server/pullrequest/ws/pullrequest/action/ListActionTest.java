/*
 * Copyright (C) 2022-2024 Michael Clarke
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
 */
package com.github.mc1arke.sonarqube.plugin.server.pullrequest.ws.pullrequest.action;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import static org.sonar.api.measures.CoreMetrics.ALERT_STATUS_KEY;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.sonar.api.server.ws.Request;
import org.sonar.api.server.ws.Response;
import org.sonar.api.server.ws.WebService;
import org.sonar.api.utils.DateUtils;
import org.sonar.db.DbClient;
import org.sonar.db.component.BranchDao;
import org.sonar.db.component.BranchDto;
import org.sonar.db.component.BranchType;
import org.sonar.db.component.SnapshotDao;
import org.sonar.db.component.SnapshotDto;
import org.sonar.db.measure.MeasureDao;
import org.sonar.db.measure.MeasureDto;
import org.sonar.db.project.ProjectDto;
import org.sonar.db.protobuf.DbProjectBranches;
import org.sonar.server.component.ComponentFinder;
import org.sonar.server.exceptions.ForbiddenException;
import org.sonar.server.user.UserSession;
import org.sonarqube.ws.ProjectPullRequests;

class ListActionTest {

    private final DbClient dbClient = mock();
    private final UserSession userSession = mock();
    private final ComponentFinder componentFinder = mock();
    private final ProtoBufWriter protoBufWriter = mock();
    private final ListAction underTest = new ListAction(dbClient, componentFinder, userSession, protoBufWriter);

    @Test
    void shouldDefineEndpointWithProjectParameter() {
        WebService.NewController newController = mock();
        WebService.NewAction newAction = mock();
        when(newAction.setHandler(any())).thenReturn(newAction);
        when(newController.createAction(any())).thenReturn(newAction);
        WebService.NewParam projectParam = mock();
        when(newAction.createParam(any())).thenReturn(projectParam);

        underTest.define(newController);

        verify(newController).createAction("list");
        verify(newAction).setHandler(underTest);
        verify(newAction).createParam("project");
        verifyNoMoreInteractions(newAction);
        verify(projectParam).setRequired(true);
        verifyNoMoreInteractions(projectParam);

        verifyNoMoreInteractions(newController);
    }

    @Test
    void shouldNotExecuteRequestIfUserDoesNotHaveAnyPermissions() {
        Request request = mock();
        when(request.mandatoryParam("project")).thenReturn("project");

        Response response = mock();

        assertThatThrownBy(() -> underTest.handle(request, response)).isInstanceOf(ForbiddenException.class);

        verifyNoMoreInteractions(protoBufWriter);
    }

}