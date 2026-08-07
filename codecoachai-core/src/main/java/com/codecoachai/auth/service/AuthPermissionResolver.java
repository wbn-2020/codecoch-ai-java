package com.codecoachai.auth.service;

import java.util.List;

public interface AuthPermissionResolver {

    List<String> resolvePermissions(List<String> roleCodes);
}
