package com.myshop.modules.product.service;

import com.myshop.modules.product.entity.ProductBrand;
import com.myshop.modules.product.vo.ProductBrandVO;

import java.util.List;

public interface ProductBrandService {

    ProductBrand getById(String id);

    List<ProductBrand> getAll();

    boolean create(ProductBrandVO vo);

    boolean update(String id, ProductBrandVO vo);

    boolean disable(String id, boolean disable);

    boolean deleteByIds(List<String> ids);
}