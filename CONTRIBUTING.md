<!--
 Copyright 2021 Google LLC

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

     https://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
-->

# How to Contribute

We'd love to accept your patches and contributions to this project. There are
just a few small guidelines you need to follow.

## Contributor License Agreement

Contributions to this project must be accompanied by a Contributor License
Agreement (CLA). You (or your employer) retain the copyright to your
contribution; this simply gives us permission to use and redistribute your
contributions as part of the project. Head over to
<https://cla.developers.google.com/> to see your current agreements on file or
to sign a new one.

You generally only need to submit a CLA once, so if you've already submitted one
(even if it was for a different project), you probably don't need to do it
again.

## Code Reviews

All submissions, including submissions by project members, require review. We
use GitHub pull requests for this purpose. Consult
[GitHub Help](https://help.github.com/articles/about-pull-requests/) for more
information on using pull requests.

Remember that the Google App Engine Standard Java8 and Java11 runtimes are used by hundred of thousands of customers!
Our code has been evolving since 2007 (internally at Google) and been pushed every 2 weeks or so, without breaking running deployed applications (the real value of a Platform As A Service product).
OK, we broke a few times, and issued a rollback as soon as possible. The code might not be perfect, and could be cleaned up a lot, but the GAE runtime has to continue to serve many applications deployed
years ago and still working without changes.

Google App Engine engineers will review the proposed changes, and will decide if ultimately it can be pushed to production. If the change cannot be pushed for whatever reasons, the change will be rollbacked.

## Community Guidelines

This project follows
[Google's Open Source Community Guidelines](https://opensource.google/conduct/).
