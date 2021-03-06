/*
 * Copyright 2015-2020 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.xtraplatform.store.legacy.rest;

/** @author fischer */
public class OrganizationDecider {
  public static String MULTI_TENANCY_ROOT = "_multi_tenancy_root_";

  public static boolean isRootOrg(String orgid) {
    return orgid == null || orgid.equals(MULTI_TENANCY_ROOT);
  }

  public static boolean isMultiTenancyRootOrg(String orgid) {
    return MULTI_TENANCY_ROOT.equals(orgid);
  }
}
