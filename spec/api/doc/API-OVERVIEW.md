# ROA Object

## Self

| Relation | Type | is-public | Public without authentication? |
|---|---|---|---|
| self-relation | Root | | Yes |

## Relations

| Relation | Type | is-public | Public without authentication?                                                                           |
|---|---|-----------|----------------------------------------------------------------------------------------------------------|
| role | Role | t         | Yes                                                                                                      |
| meta-key | Meta-Key | t         | Yes                                                                                                      |
| delegation | Delegation | t         | Yes, for an existing ID /api/delegations/7b0283c7-f316-43b3-9032-88da4b912f6d                            |
| group | Group | 403       | No; admin-only                                                                                           |
| person | Person | t         | Yes, /api/people/24ceb2d5-15ac-4c46-976e-4693f3a00628                                                                                                    |
| media-entries | Media-Entries | t         | Yes; results are permission-filtered                                                                     |
| roles | Roles | t         | Yes                                                                                                      |
| media-entry | Media-Entry | t         | Conditional; requires public metadata/preview permission                                                 |
| collections | Collections | t         | Yes; results are permission-filtered                                                                     |
| meta-datum | Meta-Datum | 404       | Conditional; requires public metadata/preview permission on its parent                                   |
| meta-datum-person | MetaDatum::Person | 404       | Conditional; requires public metadata/preview permission on its parent                                   |
| meta-keys | Meta-Keys | t         | Yes                                                                                                      |
| vocabularies | Vocabularies | t         | Yes; only publicly viewable vocabularies                                                                 |
| groups | Groups | 403       | No; admin-only                                                                                           |
| auth-info | Authentication-Info | 401       | No; returns 401 without authentication                                                                   |
| meta-datum-data-stream | Meta-Datum-Data-Stream | 404       | Conditional; requires public metadata/preview permission on its parent                                   |
| collection | Collection | t         | Conditional; requires public metadata/preview permission                                                 |
| people | Groups | 403       | No; admin-only listing                                                                                   |
| user | User | 403       | No; admin-only                                                                                           |
| media-file | Media-File | 404       | Conditional; requires public metadata/preview permission; data-stream also requires full-size permission |
| users | Users | 403       | No; admin-only                                                                                           |

“Public” here means an anonymous GET can succeed for a valid resource; it does not mean every item or field is exposed. Collection/list endpoints filter results by permissions. The 404s for ID-based relations are not evidence of access control by themselves: the relation URLs use an ID template, so the result depends on supplying an existing ID.
