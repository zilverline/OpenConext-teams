/*
 * Copyright 2012 SURFnet bv, The Netherlands
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package nl.surfnet.coin.teams.service.impl;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.rowset.SqlRowSet;

import nl.surfnet.coin.teams.domain.GroupProvider;
import nl.surfnet.coin.teams.domain.GroupProviderPreconditionTypes;
import nl.surfnet.coin.teams.domain.GroupProviderUserOauth;
import nl.surfnet.coin.teams.domain.ServiceProviderGroupAcl;
import nl.surfnet.coin.teams.service.GroupProviderService;

/**
 * SQL implementation of {@link GroupProviderService}
 */
public class GroupProviderServiceSQLImpl implements GroupProviderService {

  private static final String SELECT_GROUP_PROVIDER_BY_IDENTIFIER = "SELECT gp.id, gp.identifier, gp.name, gp.classname FROM group_provider AS gp WHERE gp.identifier ";

  // Cannot autowire because OpenConext-teams already has a JdbcTemplate defined
  // for Grouper
  // or change autowire by name instead of type
  private final JdbcTemplate jdbcTemplate;

  public GroupProviderServiceSQLImpl(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<GroupProviderUserOauth> getGroupProviderUserOauths(String userId) {
    List<GroupProviderUserOauth> gpUserOauths;

    Object[] args = new Object[1];
    args[0] = userId;

    try {
      gpUserOauths = this.jdbcTemplate
          .query(
              "SELECT gp_user_oauth.`provider_id`, gp_user_oauth.`oauth_token`, gp_user_oauth.`oauth_secret` "
                  + "FROM group_provider_user_oauth as gp_user_oauth "
                  + "WHERE gp_user_oauth.`user_id` = ?", args,
              new RowMapper<GroupProviderUserOauth>() {
                @Override
                public GroupProviderUserOauth mapRow(ResultSet rs, int rowNum)
                    throws SQLException {
                  String providerId = rs.getString("provider_id");
                  String token = rs.getString("oauth_token");
                  String secret = rs.getString("oauth_secret");
                  return new GroupProviderUserOauth(providerId, token, secret);
                }
              });

    } catch (EmptyResultDataAccessException e) {
      gpUserOauths = new ArrayList<GroupProviderUserOauth>();
    }

    return gpUserOauths;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public GroupProvider getGroupProviderByStringIdentifier(String identifier) {
    Object[] args = { identifier };
    try {
      return this.jdbcTemplate.queryForObject(
          SELECT_GROUP_PROVIDER_BY_IDENTIFIER + '=' + '?', args,
          new RowMapper<GroupProvider>() {
            @Override
            public GroupProvider mapRow(ResultSet rs, int rowNum)
                throws SQLException {
              return mapRowToGroupProvider(rs);
            }
          });
    } catch (EmptyResultDataAccessException e) {
      return null;
    }
  }

  private GroupProvider mapRowToGroupProvider(ResultSet rs) throws SQLException {
    Long id = rs.getLong("id");
    String identifier = rs.getString("identifier");
    String name = rs.getString("name");
    String gpClassName = rs.getString("classname");
    GroupProvider gp = new GroupProvider(id, identifier, name, gpClassName);
    gp.setAllowedOptions(getAllowedOptions(gp));
    gp.setUserIdPrecondition(getUserIdPreCondition(id));
    return gp;
  }

  private String getUserIdPreCondition(Long id) {
    Object[] args = { id,
        GroupProviderPreconditionTypes.USER_ID_REGEX.getStringValue() };
    try {
      return this.jdbcTemplate
          .queryForObject(
              "SELECT gppo.value    AS option_value "
                  + "FROM group_provider_precondition gpp "
                  + "LEFT JOIN group_provider_precondition_option gppo ON gpp.id = gppo.group_provider_precondition_id "
                  + "WHERE gpp.group_provider_id = ? AND gpp.className = ?;",
              args, new RowMapper<String>() {
                @Override
                public String mapRow(ResultSet resultSet, int i)
                    throws SQLException {
                  return resultSet.getString("option_value");
                }
              });
    } catch (EmptyResultDataAccessException e) {
      return null;
    }
  }

  @Override
  public List<GroupProvider> getOAuthGroupProviders(String userId) {
    List<GroupProvider> groupProviders;

    Object[] args = { userId };

    try {
      // We now select the group providers that already have an oauth access
      // token for this user.
      // Later this should change into "get all OAuth group providers the user
      // has potentially
      // access to".
      groupProviders = this.jdbcTemplate.query(
          SELECT_GROUP_PROVIDER_BY_IDENTIFIER
              + " IN (SELECT gp_user_oauth.`provider_id` "
              + "     FROM group_provider_user_oauth as gp_user_oauth "
              + "     WHERE gp_user_oauth.`user_id` = ?);", args,
          new RowMapper<GroupProvider>() {
            @Override
            public GroupProvider mapRow(ResultSet rs, int rowNum)
                throws SQLException {
              return mapRowToGroupProvider(rs);
            }
          });
    } catch (EmptyResultDataAccessException e) {
      groupProviders = new ArrayList<GroupProvider>();
    }
    return groupProviders;
  }

  /**
   * Gets the allowed options for a Group Provider
   * 
   * @param groupProvider
   *          {@link GroupProvider}
   * @return Map with allowed options
   */
  private Map<String, Object> getAllowedOptions(GroupProvider groupProvider) {
    Object[] args = new Object[1];
    args[0] = groupProvider.getId();

    Map<String, Object> options = new HashMap<String, Object>();

    final SqlRowSet sqlRowSet = this.jdbcTemplate.queryForRowSet(
        "SELECT gp_option.`name`, gp_option.`value` "
            + "FROM group_provider_option AS gp_option "
            + "WHERE gp_option.`group_provider_id` = ?;", args);

    while (sqlRowSet.next()) {
      options.put(sqlRowSet.getString("name"), sqlRowSet.getObject("value"));
    }
    return options;
  }

  /*
   * (non-Javadoc)
   * 
   * @see
   * nl.surfnet.coin.teams.service.GroupProviderService#getServiceProviderGroupAcl
   * (java.lang.String)
   */
  @Override
  public List<ServiceProviderGroupAcl> getServiceProviderGroupAcl(
      final String serviceProviderEntityId) {
    List<ServiceProviderGroupAcl> spGroupAcls;
    try {
      // Get all
      spGroupAcls = this.jdbcTemplate
          .query(
              "SELECT  group_provider_id, spentityid, allow_groups, allow_members FROM service_provider_group_acl WHERE spentityid = ?);",
              new Object[] { serviceProviderEntityId },
              new RowMapper<ServiceProviderGroupAcl>() {
                @Override
                public ServiceProviderGroupAcl mapRow(ResultSet rs, int rowNum)
                    throws SQLException {
                  long providerId = rs.getLong("group_provider_id");
                  boolean allowGroups = rs.getBoolean("allow_groups");
                  boolean allowMembers = rs.getBoolean("allow_members");
                  return new ServiceProviderGroupAcl(allowGroups, allowMembers,
                      serviceProviderEntityId, providerId);
                }
              });
    } catch (EmptyResultDataAccessException e) {
      spGroupAcls = new ArrayList<ServiceProviderGroupAcl>();
    }
    return spGroupAcls;
  }
}
