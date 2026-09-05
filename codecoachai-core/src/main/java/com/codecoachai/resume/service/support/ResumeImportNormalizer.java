package com.codecoachai.resume.service.support;

import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.resume.domain.dto.ParsedResumeStructuredDTO;
import com.codecoachai.resume.domain.vo.ResumeImportQualityReportVO;
import com.codecoachai.resume.domain.vo.ResumeImportWritePreviewVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class ResumeImportNormalizer {

    public static final String POLICY_VERSION = "resume-import-normalizer-v1";

    private static final int MAX_STRUCTURED_JSON_CHARS = 500_000;
    private static final int MAX_TEXT_CHARS = 65_000;
    private static final int MAX_LIST_ITEMS = 100;
    private static final Pattern CONTROL_CHARACTERS =
            Pattern.compile("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]");
    private static final Pattern PARSER_LABELS = Pattern.compile(
            "(?i)(AI\\s*解析简历|schemaVersion|basicInfo|targetPosition|skills|workExperiences|"
                    + "projectExperiences|educationExperiences|structuredJson|rawText)\\s*[:：=,-]+");
    private static final Pattern EMAIL =
            Pattern.compile("^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)+$");
    private static final Pattern PHONE = Pattern.compile("^\\+?[0-9]{7,20}$");
    private static final Pattern MASKED_OR_PLACEHOLDER = Pattern.compile(
            "(?i)(\\*|placeholder|unknown|not[ _-]?provided|n/?a|待补充|未填写|暂无|example\\.(com|org|net|invalid)|"
                    + "test\\.(com|invalid)|localhost)");
    private static final Set<String> PLACEHOLDER_PHONES = Set.of(
            "13800000000", "13900000000", "18888888888", "12345678901", "11111111111");

    private final ObjectMapper objectMapper;

    public NormalizationResult normalize(String structuredJson) {
        JsonNode root = parseObject(structuredJson);
        validateSchema(root);
        try {
            return normalize(objectMapper.treeToValue(root, ParsedResumeStructuredDTO.class));
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw invalidSchema("简历解析结构化结果无法转换为固定契约");
        }
    }

    public NormalizationResult normalize(ParsedResumeStructuredDTO source) {
        if (source == null) {
            throw invalidSchema("简历解析结构化结果不能为空");
        }
        List<String> warnings = new ArrayList<>();
        ParsedResumeStructuredDTO normalized = new ParsedResumeStructuredDTO();
        normalized.setSchemaVersion(ParsedResumeStructuredDTO.CURRENT_SCHEMA_VERSION);
        normalized.setBasicInfo(normalizeBasicInfo(source.getBasicInfo(), warnings));
        normalized.setTargetPosition(normalizeText(
                source.getTargetPosition(), 128, "目标岗位", warnings, false));
        normalized.setSummary(normalizeText(source.getSummary(), 2_000, "个人摘要", warnings, true));
        normalized.setSkills(normalizeValues(source.getSkills(), 100, "技能", warnings));
        normalized.setWorkExperiences(normalizeWorkExperiences(source.getWorkExperiences(), warnings));
        ProjectNormalization projects = normalizeProjects(source.getProjectExperiences(), warnings);
        normalized.setProjectExperiences(projects.projects());
        normalized.setEducationExperiences(
                normalizeEducationExperiences(source.getEducationExperiences(), warnings));

        String workExperienceText = formatWorkExperiences(normalized.getWorkExperiences(), warnings);
        String educationExperienceText =
                formatEducationExperiences(normalized.getEducationExperiences(), warnings);
        ResumeImportQualityReportVO qualityReport = buildQualityReport(
                normalized, workExperienceText, educationExperienceText, projects.duplicatesRemoved(), warnings);
        String normalizedJson = serializeAndVerify(normalized);
        String qualityReportJson = serializeQualityReport(qualityReport);
        return new NormalizationResult(
                normalized,
                normalizedJson,
                workExperienceText,
                educationExperienceText,
                qualityReport,
                qualityReportJson);
    }

    public String sourceHash(String rawText) {
        if (rawText == null) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawText.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private JsonNode parseObject(String structuredJson) {
        if (!StringUtils.hasText(structuredJson) || structuredJson.length() > MAX_STRUCTURED_JSON_CHARS) {
            throw invalidSchema("简历解析结构化结果为空或超过允许长度");
        }
        try {
            JsonNode root = objectMapper.readTree(structuredJson);
            if (root == null || !root.isObject()) {
                throw invalidSchema("简历解析结构化结果必须是 JSON 对象");
            }
            return root;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw invalidSchema("简历解析结构化结果不是合法 JSON");
        }
    }

    private void validateSchema(JsonNode root) {
        requireExactText(root, "schemaVersion", ParsedResumeStructuredDTO.CURRENT_SCHEMA_VERSION);
        JsonNode basicInfo = requireObject(root, "basicInfo");
        requireNullableText(basicInfo, "name");
        requireNullableText(basicInfo, "phone");
        requireNullableText(basicInfo, "email");
        requireNullableText(basicInfo, "location");
        requireNullableText(root, "targetPosition");
        requireNullableText(root, "summary");
        requireStringArray(root, "skills");
        validateObjectArray(root, "workExperiences", work -> {
            requireNullableText(work, "company");
            requireNullableText(work, "position");
            requireNullableText(work, "period");
            requireNullableText(work, "description");
            requireStringArray(work, "responsibilities");
            requireStringArray(work, "achievements");
        });
        validateObjectArray(root, "projectExperiences", project -> {
            requireNullableText(project, "projectName");
            requireNullableText(project, "period");
            requireNullableText(project, "background");
            requireNullableText(project, "role");
            requireNullableText(project, "description");
            requireStringArray(project, "techStack");
            requireStringArray(project, "responsibilities");
            requireStringArray(project, "coreFeatures");
            requireStringArray(project, "technicalDifficulties");
            requireStringArray(project, "optimizationResults");
            requireStringArray(project, "achievements");
        });
        validateObjectArray(root, "educationExperiences", education -> {
            requireNullableText(education, "school");
            requireNullableText(education, "degree");
            requireNullableText(education, "major");
            requireNullableText(education, "period");
            requireNullableText(education, "description");
        });
    }

    private ParsedResumeStructuredDTO.BasicInfo normalizeBasicInfo(
            ParsedResumeStructuredDTO.BasicInfo source, List<String> warnings) {
        ParsedResumeStructuredDTO.BasicInfo normalized = new ParsedResumeStructuredDTO.BasicInfo();
        if (source == null) {
            warnings.add("未识别到基本信息，请在生成后补充姓名和联系方式");
            return normalized;
        }
        normalized.setName(normalizeText(source.getName(), 64, "姓名", warnings, false));
        normalized.setLocation(normalizeText(source.getLocation(), 128, "所在地", warnings, false));
        normalized.setEmail(normalizeEmail(source.getEmail(), warnings));
        normalized.setPhone(normalizePhone(source.getPhone(), warnings));
        return normalized;
    }

    private String normalizeEmail(String value, List<String> warnings) {
        String normalized = normalizeText(value, 128, "邮箱", warnings, false);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        if (MASKED_OR_PLACEHOLDER.matcher(normalized).find() || !EMAIL.matcher(normalized).matches()) {
            warnings.add("邮箱为脱敏、占位或无效值，系统不会写入该联系人字段");
            return null;
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    private String normalizePhone(String value, List<String> warnings) {
        String normalized = normalizeText(value, 32, "手机号", warnings, false);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        if (MASKED_OR_PLACEHOLDER.matcher(normalized).find()) {
            warnings.add("手机号为脱敏或占位值，系统不会写入该联系人字段");
            return null;
        }
        String compact = normalized.replaceAll("[\\s()\\-]", "");
        String digits = compact.startsWith("+") ? compact.substring(1) : compact;
        String localDigits = digits.startsWith("86") && digits.length() == 13 ? digits.substring(2) : digits;
        if (!PHONE.matcher(compact).matches()
                || PLACEHOLDER_PHONES.contains(localDigits)
                || localDigits.matches("(\\d)\\1{6,}")) {
            warnings.add("手机号为占位或无效值，系统不会写入该联系人字段");
            return null;
        }
        return compact;
    }

    private List<ParsedResumeStructuredDTO.WorkExperience> normalizeWorkExperiences(
            List<ParsedResumeStructuredDTO.WorkExperience> values, List<String> warnings) {
        List<ParsedResumeStructuredDTO.WorkExperience> normalized = new ArrayList<>();
        if (values == null) {
            return normalized;
        }
        for (ParsedResumeStructuredDTO.WorkExperience source : values) {
            if (source == null || normalized.size() >= 50) {
                continue;
            }
            ParsedResumeStructuredDTO.WorkExperience item = new ParsedResumeStructuredDTO.WorkExperience();
            item.setCompany(normalizeText(source.getCompany(), 128, "工作单位", warnings, false));
            item.setPosition(normalizeText(source.getPosition(), 128, "工作职位", warnings, false));
            item.setPeriod(normalizeText(source.getPeriod(), 128, "工作时间", warnings, false));
            item.setDescription(normalizeText(source.getDescription(), 2_000, "工作描述", warnings, false));
            item.setResponsibilities(normalizeValues(
                    source.getResponsibilities(), 500, "工作职责", warnings));
            item.setAchievements(normalizeValues(source.getAchievements(), 500, "工作成果", warnings));
            if (hasWorkContent(item)) {
                normalized.add(item);
            }
        }
        if (values.size() > 50) {
            warnings.add("工作经历超过 50 条，仅保留前 50 条有效内容");
        }
        return normalized;
    }

    private ProjectNormalization normalizeProjects(
            List<ParsedResumeStructuredDTO.ProjectExperience> values, List<String> warnings) {
        List<ParsedResumeStructuredDTO.ProjectExperience> normalized = new ArrayList<>();
        Set<String> fingerprints = new LinkedHashSet<>();
        int duplicatesRemoved = 0;
        if (values == null) {
            return new ProjectNormalization(normalized, 0);
        }
        for (ParsedResumeStructuredDTO.ProjectExperience source : values) {
            if (source == null || normalized.size() >= MAX_LIST_ITEMS) {
                continue;
            }
            ParsedResumeStructuredDTO.ProjectExperience item =
                    new ParsedResumeStructuredDTO.ProjectExperience();
            item.setProjectName(normalizeText(source.getProjectName(), 128, "项目名称", warnings, false));
            item.setPeriod(normalizeText(source.getPeriod(), 128, "项目时间", warnings, false));
            item.setBackground(normalizeText(source.getBackground(), 2_000, "项目背景", warnings, false));
            item.setRole(normalizeText(source.getRole(), 64, "项目角色", warnings, false));
            item.setDescription(normalizeText(source.getDescription(), 2_000, "项目描述", warnings, false));
            item.setTechStack(normalizeValues(source.getTechStack(), 128, "项目技术栈", warnings));
            item.setResponsibilities(normalizeValues(
                    source.getResponsibilities(), 500, "项目职责", warnings));
            item.setCoreFeatures(normalizeValues(source.getCoreFeatures(), 500, "核心功能", warnings));
            item.setTechnicalDifficulties(normalizeValues(
                    source.getTechnicalDifficulties(), 500, "技术难点", warnings));
            item.setOptimizationResults(normalizeValues(
                    source.getOptimizationResults(), 500, "优化结果", warnings));
            item.setAchievements(normalizeValues(source.getAchievements(), 500, "项目成果", warnings));
            if (!StringUtils.hasText(item.getProjectName())) {
                if (hasProjectContent(item)) {
                    warnings.add("发现缺少项目名称的项目经历，已跳过以避免生成无法编辑的项目");
                }
                continue;
            }
            String fingerprint = projectFingerprint(item);
            if (!fingerprints.add(fingerprint)) {
                duplicatesRemoved++;
                continue;
            }
            normalized.add(item);
        }
        if (values.size() > MAX_LIST_ITEMS) {
            warnings.add("项目经历超过 100 条，仅保留前 100 条有效内容");
        }
        if (duplicatesRemoved > 0) {
            warnings.add("已按项目名称、时间和内容指纹移除 " + duplicatesRemoved + " 条重复项目");
        }
        return new ProjectNormalization(normalized, duplicatesRemoved);
    }

    private List<ParsedResumeStructuredDTO.EducationExperience> normalizeEducationExperiences(
            List<ParsedResumeStructuredDTO.EducationExperience> values, List<String> warnings) {
        List<ParsedResumeStructuredDTO.EducationExperience> normalized = new ArrayList<>();
        if (values == null) {
            return normalized;
        }
        for (ParsedResumeStructuredDTO.EducationExperience source : values) {
            if (source == null || normalized.size() >= 50) {
                continue;
            }
            ParsedResumeStructuredDTO.EducationExperience item =
                    new ParsedResumeStructuredDTO.EducationExperience();
            item.setSchool(normalizeText(source.getSchool(), 128, "学校", warnings, false));
            item.setDegree(normalizeText(source.getDegree(), 64, "学历", warnings, false));
            item.setMajor(normalizeText(source.getMajor(), 128, "专业", warnings, false));
            item.setPeriod(normalizeText(source.getPeriod(), 128, "教育时间", warnings, false));
            item.setDescription(normalizeText(source.getDescription(), 1_000, "教育说明", warnings, false));
            if (hasEducationContent(item)) {
                normalized.add(item);
            }
        }
        if (values.size() > 50) {
            warnings.add("教育经历超过 50 条，仅保留前 50 条有效内容");
        }
        return normalized;
    }

    private List<String> normalizeValues(
            List<String> values, int maxItemChars, String fieldLabel, List<String> warnings) {
        if (values == null || values.isEmpty()) {
            return new ArrayList<>();
        }
        Map<String, String> unique = new LinkedHashMap<>();
        for (String value : values) {
            if (unique.size() >= MAX_LIST_ITEMS) {
                break;
            }
            String normalized = normalizeText(value, maxItemChars, fieldLabel, warnings, false);
            if (StringUtils.hasText(normalized)) {
                unique.putIfAbsent(normalized.toLowerCase(Locale.ROOT), normalized);
            }
        }
        if (values.size() > MAX_LIST_ITEMS) {
            warnings.add(fieldLabel + "超过 100 项，仅保留前 100 项有效内容");
        }
        return new ArrayList<>(unique.values());
    }

    private String normalizeText(
            String value, int maxChars, String fieldLabel, List<String> warnings, boolean removeParserLabels) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.replace("\r\n", "\n").replace('\r', '\n');
        normalized = CONTROL_CHARACTERS.matcher(normalized).replaceAll("");
        normalized = normalized.replaceAll("(?m)[\\t ]+$", "")
                .replaceAll("(?m)^[\\t ]+", "")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
        if (removeParserLabels && PARSER_LABELS.matcher(normalized).find()) {
            normalized = PARSER_LABELS.matcher(normalized).replaceAll("")
                    .replaceAll("^[\\s,，;；:：-]+|[\\s,，;；:：-]+$", "")
                    .trim();
            warnings.add(fieldLabel + "包含解析器标签，已清理后再写入");
        }
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        if (containsStructuredJson(normalized)) {
            warnings.add(fieldLabel + "包含 JSON 数组或对象字符串，已清理后再写入");
            return null;
        }
        if (normalized.length() > maxChars) {
            warnings.add(fieldLabel + "超过长度限制，已截断为 " + maxChars + " 个字符");
            return normalized.substring(0, maxChars);
        }
        return normalized;
    }

    private String formatWorkExperiences(
            List<ParsedResumeStructuredDTO.WorkExperience> values, List<String> warnings) {
        List<String> sections = new ArrayList<>();
        for (int index = 0; index < values.size(); index++) {
            ParsedResumeStructuredDTO.WorkExperience item = values.get(index);
            List<String> lines = new ArrayList<>();
            String header = joinNonBlank(" | ", item.getPeriod(), item.getCompany(), item.getPosition());
            lines.add((index + 1) + "." + (StringUtils.hasText(header) ? " " + header : ""));
            addLine(lines, "工作内容：", item.getDescription());
            addLine(lines, "主要职责：", joinNonBlank("；", item.getResponsibilities()));
            addLine(lines, "工作成果：", joinNonBlank("；", item.getAchievements()));
            sections.add(String.join("\n", lines));
        }
        return limitFormattedText(String.join("\n\n", sections), "工作经历", warnings);
    }

    private String formatEducationExperiences(
            List<ParsedResumeStructuredDTO.EducationExperience> values, List<String> warnings) {
        List<String> sections = new ArrayList<>();
        for (int index = 0; index < values.size(); index++) {
            ParsedResumeStructuredDTO.EducationExperience item = values.get(index);
            List<String> lines = new ArrayList<>();
            String header = joinNonBlank(
                    " | ", item.getPeriod(), item.getSchool(), item.getMajor(), item.getDegree());
            lines.add((index + 1) + "." + (StringUtils.hasText(header) ? " " + header : ""));
            addLine(lines, "说明：", item.getDescription());
            sections.add(String.join("\n", lines));
        }
        return limitFormattedText(String.join("\n\n", sections), "教育经历", warnings);
    }

    private String limitFormattedText(String value, String fieldLabel, List<String> warnings) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        if (value.length() <= MAX_TEXT_CHARS) {
            return value;
        }
        warnings.add(fieldLabel + "格式化后超过数据库文本限制，已安全截断");
        return value.substring(0, MAX_TEXT_CHARS);
    }

    private ResumeImportQualityReportVO buildQualityReport(
            ParsedResumeStructuredDTO normalized,
            String workExperienceText,
            String educationExperienceText,
            int duplicatesRemoved,
            List<String> warnings) {
        ResumeImportQualityReportVO report = new ResumeImportQualityReportVO();
        report.setSchemaVersion(ParsedResumeStructuredDTO.CURRENT_SCHEMA_VERSION);
        report.setPolicyVersion(POLICY_VERSION);
        report.setDuplicateProjectsRemoved(duplicatesRemoved);
        report.setWarnings(new ArrayList<>(new LinkedHashSet<>(warnings)));

        ParsedResumeStructuredDTO.BasicInfo basicInfo = normalized.getBasicInfo();
        if (basicInfo == null || !StringUtils.hasText(basicInfo.getEmail())) {
            report.getMissingContacts().add("邮箱");
        }
        if (basicInfo == null || !StringUtils.hasText(basicInfo.getPhone())) {
            report.getMissingContacts().add("手机号");
        }
        if (basicInfo == null || !StringUtils.hasText(basicInfo.getName())) {
            report.getWarnings().add("未识别到姓名，生成后请先补充姓名");
        }
        if (!StringUtils.hasText(normalized.getTargetPosition())) {
            report.getWarnings().add("未识别到目标岗位，生成后请补充后再用于匹配或面试");
        }
        if (!hasMeaningfulResumeContent(normalized, workExperienceText, educationExperienceText)) {
            report.getBlockers().add("未识别到可写入的简历正文，请重新上传更清晰的文件");
        }
        report.setConfirmable(report.getBlockers().isEmpty());
        report.setValidationStatus(report.isConfirmable() ? "VALID" : "BLOCKED");
        report.setWritePreview(buildWritePreview(
                normalized, workExperienceText, educationExperienceText));
        return report;
    }

    private List<ResumeImportWritePreviewVO> buildWritePreview(
            ParsedResumeStructuredDTO normalized,
            String workExperienceText,
            String educationExperienceText) {
        ParsedResumeStructuredDTO.BasicInfo basicInfo = normalized.getBasicInfo();
        List<ResumeImportWritePreviewVO> preview = new ArrayList<>();
        preview.add(preview("realName", "姓名", basicInfo == null ? null : basicInfo.getName()));
        preview.add(preview("email", "邮箱",
                maskEmail(basicInfo == null ? null : basicInfo.getEmail())));
        preview.add(preview("phone", "手机号",
                maskPhone(basicInfo == null ? null : basicInfo.getPhone())));
        preview.add(preview("targetPosition", "目标岗位", normalized.getTargetPosition()));
        preview.add(preview("skillStack", "技能", joinNonBlank("、", normalized.getSkills())));
        preview.add(preview("workExperience", "工作经历", summarize(workExperienceText)));
        preview.add(preview("educationExperience", "教育经历", summarize(educationExperienceText)));
        preview.add(preview("summary", "个人摘要", summarize(normalized.getSummary())));
        String projectSummary = normalized.getProjectExperiences().isEmpty()
                ? null
                : normalized.getProjectExperiences().size() + " 个项目："
                        + joinNonBlank("、", normalized.getProjectExperiences().stream()
                        .map(ParsedResumeStructuredDTO.ProjectExperience::getProjectName)
                        .toList());
        preview.add(preview("projects", "项目经历", summarize(projectSummary)));
        return preview;
    }

    private ResumeImportWritePreviewVO preview(String fieldKey, String label, String value) {
        return new ResumeImportWritePreviewVO(
                fieldKey,
                label,
                StringUtils.hasText(value) ? value : "不写入，生成后可补充",
                StringUtils.hasText(value) ? "WILL_WRITE" : "MISSING");
    }

    private String serializeAndVerify(ParsedResumeStructuredDTO normalized) {
        try {
            String json = objectMapper.writeValueAsString(normalized);
            JsonNode roundTrip = objectMapper.readTree(json);
            validateSchema(roundTrip);
            return json;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw invalidSchema("简历解析结构化结果序列化校验失败");
        }
    }

    private String serializeQualityReport(ResumeImportQualityReportVO qualityReport) {
        try {
            return objectMapper.writeValueAsString(qualityReport);
        } catch (Exception ex) {
            throw invalidSchema("简历导入质量报告序列化失败");
        }
    }

    private void requireExactText(JsonNode root, String fieldName, String expected) {
        JsonNode value = root.get(fieldName);
        if (value == null || !value.isTextual() || !expected.equals(value.asText())) {
            throw invalidSchema("简历解析 schemaVersion 不受支持");
        }
    }

    private JsonNode requireObject(JsonNode root, String fieldName) {
        JsonNode value = root.get(fieldName);
        if (value == null || !value.isObject()) {
            throw invalidSchema("字段 " + fieldName + " 必须是对象");
        }
        return value;
    }

    private void requireNullableText(JsonNode root, String fieldName) {
        JsonNode value = root.get(fieldName);
        if (value == null || (!value.isTextual() && !value.isNull())) {
            throw invalidSchema("字段 " + fieldName + " 必须是字符串或 null");
        }
        if (value.isTextual() && containsStructuredJson(value.asText())) {
            throw invalidSchema("字段 " + fieldName + " 不允许使用 JSON 数组或对象字符串");
        }
    }

    private void requireStringArray(JsonNode root, String fieldName) {
        JsonNode value = root.get(fieldName);
        if (value == null || !value.isArray()) {
            throw invalidSchema("字段 " + fieldName + " 必须是数组");
        }
        if (value.size() > MAX_LIST_ITEMS * 2) {
            throw invalidSchema("字段 " + fieldName + " 的数组长度超过安全限制");
        }
        for (JsonNode item : value) {
            if (item == null || !item.isTextual()) {
                throw invalidSchema("字段 " + fieldName + " 只能包含字符串");
            }
            if (containsStructuredJson(item.asText())) {
                throw invalidSchema("字段 " + fieldName + " 不允许使用 JSON 数组或对象字符串");
            }
        }
    }

    private void validateObjectArray(JsonNode root, String fieldName, Consumer<JsonNode> validator) {
        JsonNode value = root.get(fieldName);
        if (value == null || !value.isArray()) {
            throw invalidSchema("字段 " + fieldName + " 必须是数组");
        }
        if (value.size() > MAX_LIST_ITEMS * 2) {
            throw invalidSchema("字段 " + fieldName + " 的数组长度超过安全限制");
        }
        for (JsonNode item : value) {
            if (item == null || !item.isObject()) {
                throw invalidSchema("字段 " + fieldName + " 只能包含对象");
            }
            validator.accept(item);
        }
    }

    private boolean containsStructuredJson(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String candidate = value.trim();
        if (!candidate.startsWith("[") && !candidate.startsWith("{")) {
            return false;
        }
        try {
            JsonNode parsed = objectMapper.readTree(candidate);
            return parsed != null && (parsed.isArray() || parsed.isObject());
        } catch (Exception ex) {
            return false;
        }
    }

    private boolean hasMeaningfulResumeContent(
            ParsedResumeStructuredDTO value, String workExperienceText, String educationExperienceText) {
        ParsedResumeStructuredDTO.BasicInfo basicInfo = value.getBasicInfo();
        return (basicInfo != null && StringUtils.hasText(basicInfo.getName()))
                || StringUtils.hasText(value.getTargetPosition())
                || StringUtils.hasText(value.getSummary())
                || !value.getSkills().isEmpty()
                || StringUtils.hasText(workExperienceText)
                || StringUtils.hasText(educationExperienceText)
                || !value.getProjectExperiences().isEmpty();
    }

    private boolean hasWorkContent(ParsedResumeStructuredDTO.WorkExperience item) {
        return StringUtils.hasText(item.getCompany())
                || StringUtils.hasText(item.getPosition())
                || StringUtils.hasText(item.getPeriod())
                || StringUtils.hasText(item.getDescription())
                || !item.getResponsibilities().isEmpty()
                || !item.getAchievements().isEmpty();
    }

    private boolean hasProjectContent(ParsedResumeStructuredDTO.ProjectExperience item) {
        return StringUtils.hasText(item.getPeriod())
                || StringUtils.hasText(item.getBackground())
                || StringUtils.hasText(item.getRole())
                || StringUtils.hasText(item.getDescription())
                || !item.getTechStack().isEmpty()
                || !item.getResponsibilities().isEmpty()
                || !item.getCoreFeatures().isEmpty()
                || !item.getTechnicalDifficulties().isEmpty()
                || !item.getOptimizationResults().isEmpty()
                || !item.getAchievements().isEmpty();
    }

    private boolean hasEducationContent(ParsedResumeStructuredDTO.EducationExperience item) {
        return StringUtils.hasText(item.getSchool())
                || StringUtils.hasText(item.getDegree())
                || StringUtils.hasText(item.getMajor())
                || StringUtils.hasText(item.getPeriod())
                || StringUtils.hasText(item.getDescription());
    }

    private String projectFingerprint(ParsedResumeStructuredDTO.ProjectExperience item) {
        return fingerprintValue(joinNonBlank("|",
                item.getProjectName(),
                item.getPeriod(),
                item.getBackground(),
                item.getDescription(),
                joinNonBlank("|", item.getResponsibilities()),
                joinNonBlank("|", item.getAchievements())));
    }

    private String fingerprintValue(String value) {
        return StringUtils.hasText(value)
                ? value.toLowerCase(Locale.ROOT).replaceAll("[\\p{P}\\p{S}\\s]+", "")
                : "";
    }

    private void addLine(List<String> lines, String prefix, String value) {
        if (StringUtils.hasText(value)) {
            lines.add(prefix + value);
        }
    }

    private String joinNonBlank(String delimiter, String... values) {
        if (values == null) {
            return null;
        }
        List<String> candidates = new ArrayList<>(values.length);
        for (String value : values) {
            candidates.add(value);
        }
        return joinNonBlank(delimiter, candidates);
    }

    private String joinNonBlank(String delimiter, List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        String joined = values.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .reduce((left, right) -> left + delimiter + right)
                .orElse(null);
        return StringUtils.hasText(joined) ? joined : null;
    }

    private String summarize(String value) {
        if (!StringUtils.hasText(value) || value.length() <= 180) {
            return value;
        }
        return value.substring(0, 180) + "...";
    }

    private String maskEmail(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        int separator = value.indexOf('@');
        if (separator <= 0) {
            return null;
        }
        String local = value.substring(0, separator);
        String maskedLocal = local.length() <= 2
                ? local.substring(0, 1) + "***"
                : local.substring(0, 2) + "***";
        return maskedLocal + value.substring(separator);
    }

    private String maskPhone(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String digits = value.replaceAll("\\D", "");
        if (digits.length() < 7) {
            return "***";
        }
        return digits.substring(0, 3) + "****" + digits.substring(digits.length() - 4);
    }

    private BusinessException invalidSchema(String message) {
        return new BusinessException(ErrorCode.PARAM_ERROR, message);
    }

    public record NormalizationResult(
            ParsedResumeStructuredDTO structuredResume,
            String normalizedJson,
            String workExperienceText,
            String educationExperienceText,
            ResumeImportQualityReportVO qualityReport,
            String qualityReportJson) {
    }

    private record ProjectNormalization(
            List<ParsedResumeStructuredDTO.ProjectExperience> projects,
            int duplicatesRemoved) {
    }
}
