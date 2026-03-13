# 1、初始化数据库：创建nacos外接数据库docai_nacos和业务数据库docai
# 2、创建用户，用户名：drw 密码：dairenwen1092
# 3、授予drw用户特定权限

CREATE database if NOT EXISTS `docai_nacos` default character set utf8mb4 collate utf8mb4_general_ci;
CREATE database if NOT EXISTS `docai` default character set utf8mb4 collate utf8mb4_general_ci;

CREATE USER IF NOT EXISTS 'drw'@'%' IDENTIFIED BY 'dairenwen1092';
ALTER USER 'drw'@'%' IDENTIFIED BY 'dairenwen1092';
grant replication slave, replication client on *.* to 'drw'@'%';

GRANT ALL PRIVILEGES ON docai_nacos.* TO  'drw'@'%';
GRANT ALL PRIVILEGES ON docai.* TO  'drw'@'%';

FLUSH PRIVILEGES;
