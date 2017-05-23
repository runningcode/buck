/*
 * Copyright 2015-present Facebook, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.facebook.buck.apple;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertFalse;

import com.dd.plist.NSArray;
import com.dd.plist.NSObject;
import com.dd.plist.NSString;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.hash.HashCode;
import java.nio.file.Paths;
import java.util.Date;
import java.util.Optional;
import org.junit.Test;

public class ProvisioningProfileStoreTest {
  private static ProvisioningProfileMetadata makeTestMetadata(
      String appID, Date expirationDate, String uuid) throws Exception {
    return makeTestMetadata(appID, expirationDate, uuid, ImmutableMap.of());
  }

  private static ProvisioningProfileMetadata makeTestMetadata(
      String appID, Date expirationDate, String uuid, ImmutableMap<String, NSObject> entitlements)
      throws Exception {
    return makeTestMetadata(appID, expirationDate, uuid, entitlements, ImmutableSet.of());
  }

  private static ProvisioningProfileMetadata makeTestMetadata(
      String appID,
      Date expirationDate,
      String uuid,
      ImmutableMap<String, NSObject> entitlements,
      ImmutableSet<HashCode> fingerprints)
      throws Exception {
    return ProvisioningProfileMetadata.builder()
        .setAppID(ProvisioningProfileMetadata.splitAppID(appID))
        .setExpirationDate(expirationDate)
        .setUUID(uuid)
        .setProfilePath(Paths.get("dummy.mobileprovision"))
        .setEntitlements(entitlements)
        .setDeveloperCertificateFingerprints(fingerprints)
        .build();
  }

  @Test
  public void testExpiredProfilesAreIgnored() throws Exception {
    ProvisioningProfileStore profiles =
        ProvisioningProfileStore.fromProvisioningProfiles(
            ImmutableList.of(
                makeTestMetadata(
                    "AAAAAAAAAA.*", new Date(0), "00000000-0000-0000-0000-000000000000")));

    Optional<ProvisioningProfileMetadata> actual =
        profiles.getBestProvisioningProfile(
            "com.facebook.test",
            ApplePlatform.IPHONEOS,
            ProvisioningProfileStore.MATCH_ANY_ENTITLEMENT,
            ProvisioningProfileStore.MATCH_ANY_IDENTITY);

    assertThat(actual, is(equalTo(Optional.empty())));
  }

  @Test
  public void testPrefixOverride() throws Exception {
    ProvisioningProfileMetadata expected =
        makeTestMetadata(
            "AAAAAAAAAA.*", new Date(Long.MAX_VALUE), "00000000-0000-0000-0000-000000000000");

    ProvisioningProfileStore profiles =
        ProvisioningProfileStore.fromProvisioningProfiles(
            ImmutableList.of(
                expected,
                makeTestMetadata(
                    "BBBBBBBBBB.com.facebook.test",
                    new Date(Long.MAX_VALUE),
                    "00000000-0000-0000-0000-000000000000")));

    NSString[] fakeKeychainAccessGroups = {new NSString("AAAAAAAAAA.*")};
    ImmutableMap<String, NSObject> fakeEntitlements =
        ImmutableMap.of("keychain-access-groups", new NSArray(fakeKeychainAccessGroups));

    Optional<ProvisioningProfileMetadata> actual =
        profiles.getBestProvisioningProfile(
            "com.facebook.test",
            ApplePlatform.IPHONEOS,
            Optional.of(fakeEntitlements),
            ProvisioningProfileStore.MATCH_ANY_IDENTITY);

    assertThat(actual.get(), is(equalTo(expected)));
  }

  @Test
  public void testEntitlementKeysAreMatched() throws Exception {
    final NSString[] fakeKeychainAccessGroups = {new NSString("AAAAAAAAAA.*")};
    final NSArray fakeKeychainAccessGroupsArray = new NSArray(fakeKeychainAccessGroups);

    ImmutableMap<String, NSObject> fakeDevelopmentEntitlements =
        ImmutableMap.of(
            "keychain-access-groups",
            fakeKeychainAccessGroupsArray,
            "aps-environment",
            new NSString("development"),
            "com.apple.security.application-groups",
            new NSArray(new NSString("foo"), new NSString("bar")));

    ImmutableMap<String, NSObject> fakeProductionEntitlements =
        ImmutableMap.of(
            "keychain-access-groups",
            fakeKeychainAccessGroupsArray,
            "aps-environment",
            new NSString("production"),
            "com.apple.security.application-groups",
            new NSArray(new NSString("foo"), new NSString("bar"), new NSString("baz")));

    ProvisioningProfileMetadata expected =
        makeTestMetadata(
            "AAAAAAAAAA.com.facebook.test",
            new Date(Long.MAX_VALUE),
            "11111111-1111-1111-1111-111111111111",
            fakeProductionEntitlements);

    ProvisioningProfileStore profiles =
        ProvisioningProfileStore.fromProvisioningProfiles(
            ImmutableList.of(
                makeTestMetadata(
                    "AAAAAAAAAA.com.facebook.test",
                    new Date(Long.MAX_VALUE),
                    "00000000-0000-0000-0000-000000000000",
                    fakeDevelopmentEntitlements),
                expected));

    Optional<ProvisioningProfileMetadata> actual =
        profiles.getBestProvisioningProfile(
            "com.facebook.test",
            ApplePlatform.IPHONEOS,
            Optional.of(
                ImmutableMap.of(
                    "keychain-access-groups",
                    fakeKeychainAccessGroupsArray,
                    "aps-environment",
                    new NSString("production"),
                    "com.apple.security.application-groups",
                    new NSArray(new NSString("foo"), new NSString("bar")))),
            ProvisioningProfileStore.MATCH_ANY_IDENTITY);

    assertThat(actual.get(), is(equalTo(expected)));

    actual =
        profiles.getBestProvisioningProfile(
            "com.facebook.test",
            ApplePlatform.IPHONEOS,
            Optional.of(
                ImmutableMap.of(
                    "keychain-access-groups",
                    fakeKeychainAccessGroupsArray,
                    "aps-environment",
                    new NSString("production"),
                    "com.apple.security.application-groups",
                    new NSArray(new NSString("foo"), new NSString("xxx")))),
            ProvisioningProfileStore.MATCH_ANY_IDENTITY);
    assertFalse(actual.isPresent());

    // Test without keychain access groups.
    actual =
        profiles.getBestProvisioningProfile(
            "com.facebook.test",
            ApplePlatform.IPHONEOS,
            Optional.of(
                ImmutableMap.of(
                    "aps-environment",
                    new NSString("production"),
                    "com.apple.security.application-groups",
                    new NSArray(new NSString("foo"), new NSString("bar")))),
            ProvisioningProfileStore.MATCH_ANY_IDENTITY);

    assertThat(actual.get(), is(equalTo(expected)));

    actual =
        profiles.getBestProvisioningProfile(
            "com.facebook.test",
            ApplePlatform.IPHONEOS,
            Optional.of(
                ImmutableMap.of(
                    "aps-environment",
                    new NSString("production"),
                    "com.apple.security.application-groups",
                    new NSArray(new NSString("foo"), new NSString("xxx")))),
            ProvisioningProfileStore.MATCH_ANY_IDENTITY);
    assertFalse(actual.isPresent());
  }

  @Test
  public void testOnlyProfilesContainingValidFingerprintsAreMatched() throws Exception {
    CodeSignIdentity validIdentity =
        CodeSignIdentity.builder()
            .setFingerprint(
                CodeSignIdentity.toFingerprint("BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB"))
            .setSubjectCommonName("iPhone Developer: Foo Bar (54321EDCBA)")
            .build();

    CodeSignIdentity otherIdentity =
        CodeSignIdentity.builder()
            .setFingerprint(
                CodeSignIdentity.toFingerprint("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"))
            .setSubjectCommonName("iPhone Developer: Foo Bar (ABCDE12345)")
            .build();

    ProvisioningProfileMetadata expected =
        makeTestMetadata(
            "AAAAAAAAAA.com.facebook.test",
            new Date(Long.MAX_VALUE),
            "11111111-1111-1111-1111-111111111111",
            ImmutableMap.of(),
            ImmutableSet.of(
                validIdentity.getFingerprint().get(), otherIdentity.getFingerprint().get()));

    ProvisioningProfileStore profiles =
        ProvisioningProfileStore.fromProvisioningProfiles(
            ImmutableList.of(
                makeTestMetadata(
                    "AAAAAAAAAA.com.facebook.test",
                    new Date(Long.MAX_VALUE),
                    "00000000-0000-0000-0000-000000000000",
                    ImmutableMap.of(),
                    ImmutableSet.of(otherIdentity.getFingerprint().get())),
                expected));

    Optional<ProvisioningProfileMetadata> actual =
        profiles.getBestProvisioningProfile(
            "com.facebook.test",
            ApplePlatform.IPHONEOS,
            ProvisioningProfileStore.MATCH_ANY_ENTITLEMENT,
            Optional.of(ImmutableList.of(validIdentity)));

    assertThat(actual.get(), is(equalTo(expected)));
  }

  @Test
  public void testGetByUUID() throws Exception {
    ProvisioningProfileMetadata expected =
        makeTestMetadata(
            "BBBBBBBBBB.*", new Date(Long.MAX_VALUE), "11111111-1111-1111-1111-111111111111");

    ProvisioningProfileStore profiles =
        ProvisioningProfileStore.fromProvisioningProfiles(
            ImmutableList.of(
                expected,
                makeTestMetadata(
                    "BBBBBBBBBB.com.facebook.test",
                    new Date(Long.MAX_VALUE),
                    "00000000-0000-0000-0000-000000000000")));

    Optional<ProvisioningProfileMetadata> actual =
        profiles.getProvisioningProfileByUUID("11111111-1111-1111-1111-111111111111");

    assertThat(actual.get(), is(equalTo(expected)));
  }

  @Test
  public void testMatchesSpecificApp() throws Exception {
    ProvisioningProfileMetadata expected =
        makeTestMetadata(
            "BBBBBBBBBB.com.facebook.test",
            new Date(Long.MAX_VALUE),
            "00000000-0000-0000-0000-000000000000");

    ProvisioningProfileStore profiles =
        ProvisioningProfileStore.fromProvisioningProfiles(
            ImmutableList.of(
                expected,
                makeTestMetadata(
                    "BBBBBBBBBB.com.facebook.*",
                    new Date(Long.MAX_VALUE),
                    "11111111-1111-1111-1111-111111111111")));

    Optional<ProvisioningProfileMetadata> actual =
        profiles.getBestProvisioningProfile(
            "com.facebook.test",
            ApplePlatform.IPHONEOS,
            ProvisioningProfileStore.MATCH_ANY_ENTITLEMENT,
            ProvisioningProfileStore.MATCH_ANY_IDENTITY);

    assertThat(actual.get(), is(equalTo(expected)));
  }

  @Test
  public void testMatchesWildcard() throws Exception {
    ProvisioningProfileMetadata expected =
        makeTestMetadata(
            "BBBBBBBBBB.*", new Date(Long.MAX_VALUE), "00000000-0000-0000-0000-000000000000");

    ProvisioningProfileStore profiles =
        ProvisioningProfileStore.fromProvisioningProfiles(ImmutableList.of(expected));

    Optional<ProvisioningProfileMetadata> actual =
        profiles.getBestProvisioningProfile(
            "com.facebook.test",
            ApplePlatform.IPHONEOS,
            ProvisioningProfileStore.MATCH_ANY_ENTITLEMENT,
            ProvisioningProfileStore.MATCH_ANY_IDENTITY);

    assertThat(actual.get(), is(equalTo(expected)));
  }
}
