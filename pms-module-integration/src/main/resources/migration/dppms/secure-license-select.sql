SELECT id,licenseCode,sn,specModel,contract,contractType,item,status FROM pm_project_license_info_from_license WHERE FIND_IN_SET(id,?) ORDER BY id
