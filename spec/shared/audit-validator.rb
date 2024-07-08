BASE_CHANGE_AUDIT_OPTIONS = {expected_requested_count: 0, expected_response_count: 0}
OPT_CHANGE_AUDITS_ONLY = BASE_CHANGE_AUDIT_OPTIONS.merge(distinct: false)
OPT_DISTINCT_CHANGE_AUDITS_ONLY = BASE_CHANGE_AUDIT_OPTIONS.merge(distinct: true)

def expect_audit_entries_count(expected_request_count, expected_change_count, expected_response_count)
  [AuditedRequest, AuditedChange, AuditedResponse].zip([expected_request_count, expected_change_count, expected_response_count]).each do |model, expected_count|
    expect(model.all.count).to eq(expected_count)
  end
end

def expect_audit_entries(expected_auditedrequest_info, expected_auditchange_list, expected_auditedresponse_status,
  options = {distinct: false,
             expected_requested_count: 1, expected_response_count: 1})

  list = AuditedChange.all.map { |a| "#{a.tg_op} #{a.table_name}" }
  if options[:distinct]
    expect(list.uniq.sort).to be == expected_auditchange_list.uniq.sort
  else
    expect(list).to be == expected_auditchange_list
  end

  all_request_audits = AuditedRequest.all
  if options[:expected_requested_count] == 0
    expect(all_request_audits.count).to eq(0)
  else
    expect(all_request_audits.count).to eq(1)
    expect(all_request_audits.first.method + " " + all_request_audits.first.path).to eq(expected_auditedrequest_info)
  end

  all_response_audits = AuditedResponse.all
  if options[:expected_response_count] == 0
    expect(all_response_audits.count).to eq(0)
  else
    expect(all_response_audits.count).to eq(1)
    expect(all_response_audits.first.status).to eq(expected_auditedresponse_status)
  end
end

def lists_of_maps_different?(list1, list2)
  return true unless list1.length == list2.length

  list1.each_with_index do |map1, index|
    map2 = list2[index]
    return true unless map1 == map2
  end

  false
end
