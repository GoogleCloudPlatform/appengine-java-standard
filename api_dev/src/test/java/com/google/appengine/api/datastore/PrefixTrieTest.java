/*
 * Copyright 2021 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.appengine.api.datastore;

import static com.google.common.truth.Truth.assertThat;

import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class PrefixTrieTest {
  @Test
  public void testPutGet() {
    PrefixTrie<Object> trie = new PrefixTrie<>();
    Object foo = new Object();
    trie.put("foo:", foo);
    Object a = new Object();
    trie.put("a:", a);
    assertThat(trie.get("foo:bar")).isSameInstanceAs(foo);
    assertThat(trie.get("a:bar")).isSameInstanceAs(a);
    assertThat(trie.get("tee:bar")).isNull();
    assertThat(trie.get("foobar")).isNull();
  }

  @Test
  public void testClosePrefixes() {
    PrefixTrie<Object> trie = new PrefixTrie<>();
    trie.put("fooa", new Object());
    trie.put("foob", new Object());
  }

  @Test
  public void testLongestPrefixWins() {
    PrefixTrie<String> trie = new PrefixTrie<>();
    trie.put("foobar", "1");
    trie.put("foo", "2");
    assertThat(trie.get("foob")).isEqualTo("2");

    PrefixTrie<String> trie2 = new PrefixTrie<>();
    trie2.put("foo", "1");
    trie2.put("foobar", "2");
    assertThat(trie2.get("foob")).isEqualTo("1");
  }

  @Test
  public void testEmptyPrefix() {
    PrefixTrie<String> trie = new PrefixTrie<>();
    trie.put("", "empty");
    trie.put("abc", "nonempty");
    assertThat(trie.get("cd")).isEqualTo("empty");
  }

  @Test
  public void testAsMap() {
    PrefixTrie<String> trie = new PrefixTrie<>('0', '9');
    Map<String, String> golden = new HashMap<>();

    assertThat(trie.toMap()).isEqualTo(golden);

    trie.put("1", "one");
    trie.put("12", "one-two");
    trie.put("14", "one-four");
    golden.put("1", "one");
    golden.put("12", "one-two");
    golden.put("14", "one-four");
    assertThat(trie.toMap()).isEqualTo(golden);
  }

  @Test
  public void testRemove() {
    PrefixTrie<String> trie = new PrefixTrie<>();
    trie.put("foo", "1");
    trie.put("foobar", "2");
    assertThat(trie.remove("foobarbaz")).isNull();
    assertThat(trie.remove("foo")).isEqualTo("1");
    assertThat(trie.remove("foo")).isNull();
    assertThat(trie.get("foobarbaz")).isEqualTo("2");
    assertThat(trie.get("foob")).isNull();
    assertThat(trie.get("foo")).isNull();
    assertThat(trie.remove("foobar")).isEqualTo("2");

    assertThat(trie.put("foo", "3")).isNull();
    assertThat(trie.get("foobar")).isEqualTo("3");
  }
}
