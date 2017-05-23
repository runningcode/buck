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

package com.facebook.buck.query;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import com.facebook.buck.query.QueryEnvironment.Argument;
import com.facebook.buck.testutil.integration.TemporaryPaths;
import com.google.common.collect.ImmutableList;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class QueryParserTest {

  private QueryEnvironment queryEnvironment;

  @Rule public TemporaryPaths tmp = new TemporaryPaths();

  @Rule public ExpectedException thrown = ExpectedException.none();

  @Before
  public void makeEnvironment() {
    queryEnvironment = createMock(QueryEnvironment.class);
    expect(queryEnvironment.getFunctions()).andStubReturn(QueryEnvironment.DEFAULT_QUERY_FUNCTIONS);
    replay(queryEnvironment);
  }

  @Test
  public void testDeps() throws Exception {
    ImmutableList<Argument> args = ImmutableList.of(Argument.of(new TargetLiteral("//foo:bar")));
    QueryExpression expected = new FunctionExpression(new DepsFunction(), args);

    String query = "deps('//foo:bar')";
    QueryExpression result = QueryParser.parse(query, queryEnvironment);
    assertThat(result, is(equalTo(expected)));
  }

  @Test
  public void testTestsOfDepsSet() throws Exception {
    ImmutableList<TargetLiteral> args =
        ImmutableList.of(new TargetLiteral("//foo:bar"), new TargetLiteral("//other:lib"));
    QueryExpression depsExpr =
        new FunctionExpression(
            new DepsFunction(), ImmutableList.of(Argument.of(new SetExpression(args))));
    QueryExpression testsofExpr =
        new FunctionExpression(new TestsOfFunction(), ImmutableList.of(Argument.of(depsExpr)));

    String query = "testsof(deps(set('//foo:bar' //other:lib)))";
    QueryExpression result = QueryParser.parse(query, queryEnvironment);
    assertThat(result, is(equalTo(testsofExpr)));
  }

  @Test
  public void shouldThrowExceptionWhenUnexpetedComma() throws QueryException {
    String query = "testsof(deps(set('//foo:bar', //other:lib)))";
    thrown.expect(QueryException.class);
    thrown.expectMessage("syntax error at ', //other:lib )'");
    QueryParser.parse(query, queryEnvironment);
  }

  @Test
  public void shouldThrowExceptionWhenMissingParens() throws QueryException {
    String query = "testsof(deps(set('//foo:bar' //other:lib))";
    thrown.expect(QueryException.class);
    thrown.expectMessage("premature end of input");
    QueryParser.parse(query, queryEnvironment);
  }

  @Test
  public void shouldThrowExceptionWhenExtraParens() throws QueryException {
    String query = "testsof(deps(set('//foo:bar' //other:lib))))";
    thrown.expect(QueryException.class);
    thrown.expectMessage(
        "Unexpected token ')' after query expression 'testsof(deps(set(//foo:bar //other:lib)))'");
    QueryParser.parse(query, queryEnvironment);
  }

  @Test
  public void testUsefulErrorOnInsufficientArguments() throws QueryException {
    thrown.expect(QueryException.class);
    thrown.expectCause(Matchers.isA(QueryException.class));
    thrown.expectMessage("rdeps(EXPRESSION, EXPRESSION [, INTEGER ])");
    thrown.expectMessage("https://buckbuild.com/command/query.html#rdeps");
    String query = "rdeps('')";
    QueryParser.parse(query, queryEnvironment);
  }

  @Test
  public void testUsefulErrorOnIncorrectArguments() throws QueryException {
    thrown.expect(QueryException.class);
    thrown.expectCause(Matchers.isA(QueryException.class));
    thrown.expectMessage("expected an integer literal");
    thrown.expectMessage("deps(EXPRESSION [, INTEGER [, EXPRESSION ] ])");
    thrown.expectMessage("https://buckbuild.com/command/query.html#deps");
    String query = "deps(//foo:bar, //bar:foo)";
    QueryParser.parse(query, queryEnvironment);
  }
}
