package com.example.service.impl.examinePaper;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.mapper.CourseExamineChildMethodsMAPPER;
import com.example.mapper.CourseExamineMethodsMAPPER;
import com.example.mapper.examinePaper.StudentInformationMAPPER;
import com.example.mapper.examinePaper.StudentUsualScoreMAPPER;
import com.example.object.CourseExamineChildMethods;
import com.example.object.CourseExamineMethods;
import com.example.object.finalExamine.StudentInformation;
import com.example.object.finalExamine.StudentUsualScore;
import com.example.service.examinePaper.StudentUsualScoreSERVICE;
import com.example.utility.DataResponses;
import org.apache.poi.hssf.usermodel.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.example.utility.export.export;

@Service
public class StudentUsualScoreServiceIMPL extends ServiceImpl<StudentUsualScoreMAPPER, StudentUsualScore> implements StudentUsualScoreSERVICE {

    @Autowired
    private StudentInformationMAPPER studentInformationMAPPER;
    @Autowired
    private StudentUsualScoreMAPPER studentUsualScoreMAPPER;

    @Autowired
    private CourseExamineMethodsMAPPER courseExamineMethodsMAPPER;

    @Autowired
    private CourseExamineChildMethodsMAPPER courseExamineChildMethodsMAPPER;

    //获取所有学生平时成绩信息
    @Override
    public List<StudentUsualScore> getAllStudent(int courseId) {
        int[] emptyArray = new int[0];
        refreshStudentScore(courseId);
        List<StudentUsualScore> allStudent = studentUsualScoreMAPPER.getAllStudent(courseId);
        for (StudentUsualScore studentUsualScore : allStudent) {
            if (studentUsualScore.getScoreDetails() == null) {
                studentUsualScore.setScoreResponse(emptyArray);
            } else {
                studentUsualScore.setScoreResponse(JSONArray.parseArray(studentUsualScore.getScoreDetails()));
            }
        }
        return allStudent;
    }

    //获取老师设置的学生平时成绩子项目分类
    @Override
    public List<String> getUsualExamMethods(int courseID) {
        List<String> strings = new ArrayList<>();
        QueryWrapper<CourseExamineMethods> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("course_id", courseID);
        List<CourseExamineMethods> courseExamineMethods = courseExamineMethodsMAPPER.selectList(queryWrapper);

        int id = 0;
        for (CourseExamineMethods courseExamineMethods1 : courseExamineMethods) {
            if (Objects.equals(courseExamineMethods1.getExamineItem(), "平时考核成绩")) {
                id = courseExamineMethods1.getId();
            }
        }

        QueryWrapper<CourseExamineChildMethods> queryWrapper2 = new QueryWrapper<>();
        queryWrapper2.eq("course_examine_methods_id", id);
        queryWrapper2.orderByAsc("id");
        List<CourseExamineChildMethods> courseExamineChildMethods = courseExamineChildMethodsMAPPER.selectList(queryWrapper2);

        for (CourseExamineChildMethods courseExamineChildMethods1 : courseExamineChildMethods) {
            strings.add(courseExamineChildMethods1.getExamineChildItem());
        }
        return strings;
    }

    //获取老师设置的学生平时成绩子项目百分比
    public List<Integer> getUsualExamPercentage(int courseId) {
        List<Integer> percentage = new ArrayList<>();
        QueryWrapper<CourseExamineMethods> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("course_id", courseId);
        List<CourseExamineMethods> courseExamineMethods = courseExamineMethodsMAPPER.selectList(queryWrapper);

        int id = 0;
        for (CourseExamineMethods courseExamineMethods1 : courseExamineMethods) {
            if (Objects.equals(courseExamineMethods1.getExamineItem(), "平时考核成绩")) {
                id = courseExamineMethods1.getId();
            }
        }

        QueryWrapper<CourseExamineChildMethods> queryWrapper2 = new QueryWrapper<>();
        queryWrapper2.eq("course_examine_methods_id", id);
        queryWrapper2.orderByAsc("id");
        List<CourseExamineChildMethods> courseExamineChildMethods = courseExamineChildMethodsMAPPER.selectList(queryWrapper2);

        for (CourseExamineChildMethods courseExamineChildMethods1 : courseExamineChildMethods) {
            percentage.add(courseExamineChildMethods1.getChildPercentage());
        }
        return percentage;
    }

    //学生平时总成绩设置和刷新
    @Override
    public void refreshStudentScore(int courseId) {

        List<Integer> usualExamPercentage = getUsualExamPercentage(courseId);

        List<StudentUsualScore> allStudent = studentUsualScoreMAPPER.getAllStudent(courseId);
        for (StudentUsualScore score : allStudent) {
            if (score.getUsualScoreId() != null) {
                StudentUsualScore usualScore = new StudentUsualScore();
                usualScore.setUsualScoreId(score.getUsualScoreId());
                usualScore.setScoreDetails(score.getScoreDetails());
                usualScore.setStudentId(score.getId());

                double sum = 0;
                JSONArray objects = JSONArray.parseArray(score.getScoreDetails());
                for (int i = 0; i < objects.size(); i++) {
                    sum += Double.parseDouble((String) objects.get(i)) * usualExamPercentage.get(i) * 0.01;
                }
                usualScore.setScore(Math.ceil(sum));

                QueryWrapper<StudentUsualScore> queryWrapper = new QueryWrapper<>();
                queryWrapper.eq("usual_score_id", usualScore.getUsualScoreId());
                studentUsualScoreMAPPER.update(usualScore, queryWrapper);

            }
        }
    }

    //学生成绩表格导出
    @Override
    public ResponseEntity<byte[]> exportStudentUsualScore(int courseId) throws IOException {

        //工作簿事例
        int rowIndex = 1;
        int columIndex = 0;

        Workbook workbook = new HSSFWorkbook();
        Sheet sheet = workbook.createSheet();

        //单元格样式
        CellStyle style = workbook.createCellStyle();
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);


        //表头设置
        Row row1 = sheet.createRow(0);
        row1.setRowStyle(style);
        Row row2 = sheet.createRow(1);
        row2.setRowStyle(style);
        Row row3 = sheet.createRow(2);
        row3.setRowStyle(style);
        Row row4 = sheet.createRow(3);
        row4.setRowStyle(style);

        CellRangeAddress mergedRegion = new CellRangeAddress(1, 3, 0, 0);
        sheet.addMergedRegion(mergedRegion);
        row2.createCell(0).setCellValue("学号");
        export.reloadCellStyle(mergedRegion, sheet, style);
        sheet.setColumnWidth(0, 20 * 256);

        mergedRegion = new CellRangeAddress(1, 3, 1, 1);
        sheet.addMergedRegion(mergedRegion);
        row2.createCell(1).setCellValue("姓名");
        export.reloadCellStyle(mergedRegion, sheet, style);
        sheet.autoSizeColumn(1);

        mergedRegion = new CellRangeAddress(1, 3, 2, 2);
        sheet.addMergedRegion(mergedRegion);
        row2.createCell(2).setCellValue("班级");
        export.reloadCellStyle(mergedRegion, sheet, style);
        sheet.setColumnWidth(2, 20 * 256);

        //考核条目
        List<String> usualExamMethods = getUsualExamMethods(courseId);
        columIndex = 3;
        for (String dataExtend : usualExamMethods) {
            mergedRegion = new CellRangeAddress(1, 3, columIndex, columIndex);
            sheet.addMergedRegion(mergedRegion);
            row2.createCell(columIndex).setCellValue(dataExtend);
            export.reloadCellStyle(mergedRegion, sheet, style);
            columIndex++;
        }

        mergedRegion = new CellRangeAddress(1, 3, columIndex, columIndex);
        sheet.addMergedRegion(mergedRegion);
        row2.createCell(columIndex).setCellValue("总分");
        export.reloadCellStyle(mergedRegion, sheet, style);

        rowIndex = 4;
        //学生列表
        List<StudentUsualScore> allStudent = getAllStudent(courseId);
        for (StudentUsualScore score : allStudent) {
            Row eachRow = sheet.createRow(rowIndex);
            eachRow.setRowStyle(style);

            export.valueToCell(sheet, rowIndex, 0, score.getStudentNumber(), style);
            export.valueToCell(sheet, rowIndex, 1, score.getStudentName(), style);
            export.valueToCell(sheet, rowIndex, 2, score.getClassName(), style);

            //成绩
            int index = 3;
            JSONArray objects = JSONArray.parseArray(score.getScoreDetails());
            if (objects == null) {

            } else {
                for (Object object : objects) {
//                    eachRow.createCell(index).setCellValue(object.toString());
//                    eachRow.getCell(index).setCellStyle(style);
                    export.valueToCell(sheet, rowIndex, index, object.toString(), style);
                    index++;
                }
            }
            rowIndex++;
        }

        //写入文件
        //使用字节数组读取
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        workbook.write(byteArrayOutputStream);
        byte[] bytes = byteArrayOutputStream.toByteArray();

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment;filename=template.xls");
        headers.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE);
        return ResponseEntity.ok()
                .headers(headers)
                .body(bytes);
    }

    //学生平时成绩导入
    @Override
    @Transactional
    public DataResponses inputStudentUsualScore(MultipartFile file, String courseId) {
        try {
            HSSFWorkbook workbook = new HSSFWorkbook(file.getInputStream());
            HSSFSheet sheet = workbook.getSheetAt(0);

            DataFormatter formatter = new DataFormatter();
            //遍历列
            for (int rowIndex = 4; rowIndex < sheet.getLastRowNum() + 1; rowIndex++) {
                Row row = sheet.getRow(rowIndex);

                QueryWrapper<StudentInformation> queryWrapper = new QueryWrapper<>();
                queryWrapper.eq("student_number", formatter.formatCellValue(row.getCell(0)));
                queryWrapper.eq("course_id", courseId);
                StudentInformation information = studentInformationMAPPER.selectOne(queryWrapper);

                StudentInformation student = new StudentInformation();
                student.setStudentName(formatter.formatCellValue(row.getCell(1)));
                student.setClassName(formatter.formatCellValue(row.getCell(2)));

                Integer id = 0;
                if (information == null) {
                    student.setStudentNumber(formatter.formatCellValue(row.getCell(0)));
                    student.setCourseId(courseId);

                    studentInformationMAPPER.insert(student);
                    id = studentInformationMAPPER.selectOne(queryWrapper).getId();
                } else {
                    id = information.getId();
                    student.setId(id);

                    studentInformationMAPPER.updateById(student);
                }

                List<String> usualExamMethods = getUsualExamMethods(Integer.parseInt(courseId));
                int columIndex = 3;
                List<String> strings = new ArrayList<>();
                for (int i = 0; i < usualExamMethods.size(); i++) {
                    strings.add(formatter.formatCellValue(row.getCell(columIndex)));
                    columIndex++;
                }
                StudentUsualScore studentUsualScore = new StudentUsualScore();
                studentUsualScore.setStudentId(id);
                studentUsualScore.setScoreDetails(JSON.toJSONString(strings));

                QueryWrapper<StudentUsualScore> queryWrapper2 = new QueryWrapper<>();
                queryWrapper2.eq("student_id", id);

                if (studentUsualScoreMAPPER.getOneStudent(id).getUsualScoreId() == null) {
                    studentUsualScoreMAPPER.insert(studentUsualScore);
                } else {
                    studentUsualScoreMAPPER.update(studentUsualScore, queryWrapper2);
                }
                refreshStudentScore((Integer.parseInt(courseId)));
            }
            return new DataResponses(true, "导入成功");

        } catch (IOException exception) {
            return new DataResponses(false, "导入失败，表格数据有缺失");
        }
    }
}
